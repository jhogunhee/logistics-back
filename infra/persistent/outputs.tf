output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_ids" {
  value = [for s in aws_subnet.public : s.id]
}

output "db_subnet_group_name" {
  value = aws_db_subnet_group.db.name
}

output "alb_security_group_id" {
  value = aws_security_group.alb.id
}

output "app_security_group_id" {
  value = aws_security_group.app.id
}

output "db_security_group_id" {
  value = aws_security_group.db.id
}

output "ecr_repository_url" {
  value = aws_ecr_repository.back.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.this.name
}

output "ecs_task_exec_role_arn" {
  value = aws_iam_role.ecs_task_exec.arn
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.app.name
}

output "db_password_param" {
  value = var.db_password_param
}

output "cloudfront_domain_name" {
  value = aws_cloudfront_distribution.api.domain_name
}

output "github_deploy_role_arn" {
  value       = aws_iam_role.github_deploy.arn
  description = "GitHub Actions 워크플로의 role-to-assume 값"
}
