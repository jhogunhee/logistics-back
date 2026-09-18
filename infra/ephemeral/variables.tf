variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project" {
  type    = string
  default = "wareflow"
}

# 기본값을 두지 않는다. 기본값이 있으면 ECR에 없는 이미지를 가리킨 채 태스크가 조용히 pull 실패한다.
# 마지막 workflow_dispatch가 올린 커밋 SHA를 넘긴다.
variable "image_tag" {
  type = string
}

# 매번 이 스냅샷에서 복원한다. schema.sql이 바뀌면 스냅샷을 다시 떠서 이 값을 바꾼다.
variable "snapshot_identifier" {
  type    = string
  default = "wareflow-golden-20260918"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_name" {
  type    = string
  default = "wareflow"
}

variable "db_username" {
  type    = string
  default = "postgres"
}

# 최초 적재용 도구다. 평소 켤 때 RDS는 처음부터 private이다.
variable "db_public" {
  type    = bool
  default = false
}

# AWS용 Pages 프로젝트 주소. 라이브 쪽 주소는 넣지 않는다.
variable "cors_allowed_origins" {
  type    = string
  default = "https://wareflow-aws.pages.dev"
}

variable "task_cpu" {
  type    = string
  default = "512"
}

variable "task_memory" {
  type    = string
  default = "1024"
}

variable "desired_count" {
  type    = number
  default = 1
}

# 스프링 부트 기동이 수십 초 걸린다. 짧으면 기동 중인 태스크를 unhealthy로 보고 재시작 루프에 빠진다.
variable "health_check_grace_period" {
  type    = number
  default = 180
}
