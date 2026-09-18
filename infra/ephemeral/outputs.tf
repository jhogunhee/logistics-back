# up.sh가 이 값을 꺼내 persistent의 CloudFront 오리진에 넣는다.
output "alb_dns_name" {
  value = aws_lb.this.dns_name
}

output "rds_endpoint" {
  value = aws_db_instance.this.address
}

output "db_url" {
  value = local.db_url
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "task_definition_arn" {
  value = aws_ecs_task_definition.app.arn
}
