resource "aws_lb" "this" {
  name               = "${var.project}-alb"
  load_balancer_type = "application"
  internal           = false
  subnets            = local.p.public_subnet_ids
  security_groups    = [local.p.alb_security_group_id]
  ip_address_type    = "ipv4"
}

resource "aws_lb_target_group" "app" {
  name        = "${var.project}-tg"
  target_type = "ip" # Fargate는 awsvpc라 인스턴스가 아니라 IP로 등록된다
  protocol    = "HTTP"
  port        = 8080
  vpc_id      = local.p.vpc_id

  health_check {
    path     = "/health"
    port     = "traffic-port"
    protocol = "HTTP"
    interval = 30

    # /health가 204를 준다. 타겟그룹 기본 성공코드는 200이라 그대로 두면 정상 태스크가 계속 unhealthy가 된다.
    matcher = "200-299"

    healthy_threshold   = 2
    unhealthy_threshold = 2
  }
}

# CloudFront가 HTTPS를 맡는다. 여기는 평문이지만 보안그룹이 CloudFront 프리픽스만 허용한다.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}
