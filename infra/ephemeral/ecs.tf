locals {
  db_url = "jdbc:postgresql://${aws_db_instance.this.address}:5432/${var.db_name}?sslmode=require"
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${var.project}-back"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = local.p.ecs_task_exec_role_arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = "${local.p.ecr_repository_url}:${var.image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          protocol      = "tcp"
        }
      ]

      # AWS에 맞춘 차이는 전부 여기서 준다. Dockerfile과 application.properties는 Render와 공유하므로 고치지 않는다.
      # PORT는 넣지 않는다 — Fargate가 주입하지 않으므로 server.port=${PORT:8080}이 8080으로 떨어진다.
      environment = [
        { name = "DB_URL", value = local.db_url },
        { name = "DB_USERNAME", value = var.db_username },
        { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },

        # 프론트와 사이트가 갈려 이 둘이 빠지면 로그인 응답은 정상인데 브라우저가 쿠키를 버린다.
        { name = "SESSION_COOKIE_SAME_SITE", value = "none" },
        { name = "SESSION_COOKIE_SECURE", value = "true" },

        # 기본값 true면 모든 쿼리가 CloudWatch로 들어간다.
        { name = "DECORATOR_DATASOURCE_P6SPY_ENABLE_LOGGING", value = "false" },

        # 75%면 힙 768MB에 Metaspace·CodeCache가 절대값으로 더 붙어 1GB에 육박한다.
        { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=60 -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -Xss512k" },

        # 로그인 시도 제한. 아래 redis 컨테이너와 같은 태스크라 REDIS_HOST 기본값(localhost)이 그대로 맞는다
        { name = "LOGIN_GUARD_REDIS_ENABLED", value = "true" },
      ]

      dependsOn = [
        { containerName = "redis", condition = "START" }
      ]

      secrets = [
        { name = "DB_PASSWORD", valueFrom = local.db_password_param_arn }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = local.p.log_group_name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    },

    # 로그인 시도 제한 카운터. 별도 서비스(ElastiCache)를 두지 않고 같은 태스크에 붙인다 —
    # 담기는 것이 TTL 5분짜리 카운터뿐이라 태스크와 함께 사라져도 잃을 게 없고, 그래서
    # ephemeral 스택의 시간당 비용이 그대로다. Docker Hub는 pull 한도가 있어 ECR Public을 쓴다.
    {
      name  = "redis"
      image = "public.ecr.aws/docker/library/redis:7-alpine"
      # 죽어도 태스크를 내리지 않는다. 가드는 Redis가 없으면 통과시키므로 로그인은 계속 된다
      essential = false
      command = [
        "redis-server",
        "--save", "",
        "--appendonly", "no",
        "--maxmemory", "32mb",
        "--maxmemory-policy", "allkeys-lru",
      ]
      # 앱 힙(태스크 메모리의 60%)을 침범하지 않도록 상한을 둔다
      memory = 64

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = local.p.log_group_name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "app" {
  name            = "${var.project}-svc"
  cluster         = local.p.ecs_cluster_name
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets         = local.p.public_subnet_ids
    security_groups = [local.p.app_security_group_id]

    # NAT가 없어 ECR·CloudWatch·SSM으로 나가는 길이 IGW뿐이고, IGW로 나가려면 공인 IP가 필요하다.
    # 없으면 CannotPullContainerError로 멈춘다.
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }

  health_check_grace_period_seconds = var.health_check_grace_period

  # CI(workflow_dispatch)가 올린 리비전을 Terraform이 되돌리지 않게 한다.
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_lb_listener.http]
}
