data "aws_caller_identity" "current" {}

locals {
  db_password_param_arn = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${var.db_password_param}"
}

# ── 태스크 실행 역할 ──────────────────────────────────────────────
# 컨테이너가 아니라 ECS 에이전트가 쓴다. 이미지를 받고, 로그를 쓰고, SSM에서 비밀번호를 읽는다.
data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_exec" {
  name               = "${var.project}-ecs-task-exec"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_task_exec_managed" {
  role       = aws_iam_role.ecs_task_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# 관리형 정책에는 SSM 읽기가 없다. 빠뜨리면 태스크가 ResourceInitializationError로 죽는다.
data "aws_iam_policy_document" "ssm_read" {
  statement {
    actions   = ["ssm:GetParameters"]
    resources = [local.db_password_param_arn]
  }
}

resource "aws_iam_role_policy" "ecs_task_exec_ssm" {
  name   = "${var.project}-ssm-read"
  role   = aws_iam_role.ecs_task_exec.id
  policy = data.aws_iam_policy_document.ssm_read.json
}

# ── GitHub Actions용 OIDC ────────────────────────────────────────
# 장기 액세스 키를 두지 않기 위해, 워크플로가 이 역할을 임시로 맡는다.
resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # 이 레포의 이 브랜치에서 돌 때만 맡을 수 있다.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/${var.github_branch}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${var.project}-gha-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

data "aws_iam_policy_document" "github_deploy" {
  # ECR 로그인 토큰은 리소스를 지정할 수 없다.
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
    ]
    resources = [aws_ecr_repository.back.arn]
  }

  # 새 리비전을 올리고 서비스를 갱신한다. 서비스가 없을 때(내려가 있을 때)는 워크플로가 실패한다 — 의도한 동작이다.
  statement {
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DescribeTaskDefinition",
      "ecs:DescribeServices",
      "ecs:UpdateService",
    ]
    resources = ["*"]
  }

  # 태스크 정의에 실행 역할을 붙이려면 필요하다.
  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_task_exec.arn]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${var.project}-gha-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
