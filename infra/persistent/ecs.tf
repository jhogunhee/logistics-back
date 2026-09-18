# 이미 콘솔에서 만들어 이미지를 올려 둔 리포지토리를 Terraform 관리로 가져온다.
# 새로 만들면 push해 둔 이미지를 잃는다.
import {
  to = aws_ecr_repository.back
  id = "wareflow-back"
}

resource "aws_ecr_repository" "back" {
  name                 = "${var.project}-back"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = false
  }
}

# 태그가 불변이라 커밋마다 이미지가 쌓인다. 최근 것만 남긴다.
resource "aws_ecr_lifecycle_policy" "back" {
  repository = aws_ecr_repository.back.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecs_cluster" "this" {
  name = "${var.project}-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled"
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${var.project}-back"
  retention_in_days = var.log_retention_days
}
