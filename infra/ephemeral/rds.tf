# 매번 골든 스냅샷에서 복원한다. 데이터 적재도, private subnet에 접속할 경로를 여는 일도 없어진다.
# 엔진 버전·마스터 사용자·비밀번호·데이터는 스냅샷이 그대로 가져온다.
resource "aws_db_instance" "this" {
  identifier          = "${var.project}-db"
  snapshot_identifier = var.snapshot_identifier

  instance_class    = var.db_instance_class
  allocated_storage = 20
  storage_type      = "gp3"
  multi_az          = false

  db_subnet_group_name   = local.p.db_subnet_group_name
  vpc_security_group_ids = [local.p.db_security_group_id]
  publicly_accessible    = var.db_public

  # 골든 스냅샷이 따로 있으므로 내릴 때마다 스냅샷을 남길 이유가 없다.
  backup_retention_period  = 0
  skip_final_snapshot      = true
  delete_automated_backups = true
  deletion_protection      = false

  performance_insights_enabled = false
  monitoring_interval          = 0
  apply_immediately            = true
}
