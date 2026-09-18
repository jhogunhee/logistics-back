variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project" {
  type    = string
  default = "wareflow"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

# 퍼블릭 = ALB·Fargate, db = RDS. AZ 2개는 ALB와 RDS 서브넷 그룹의 최소 요건이다.
variable "public_subnets" {
  type = map(object({ az = string, cidr = string }))
  default = {
    a = { az = "ap-northeast-2a", cidr = "10.0.1.0/24" }
    c = { az = "ap-northeast-2c", cidr = "10.0.2.0/24" }
  }
}

variable "db_subnets" {
  type = map(object({ az = string, cidr = string }))
  default = {
    a = { az = "ap-northeast-2a", cidr = "10.0.11.0/24" }
    c = { az = "ap-northeast-2c", cidr = "10.0.12.0/24" }
  }
}

# ephemeral이 없을 때 CloudFront가 가리킬 임시 주소.
# 실재하는 도메인을 쓰면 오리진 요청 정책이 넘기는 쿠키가 그쪽으로 간다. .invalid는 해석되지 않는 예약 도메인이다.
variable "alb_dns_name" {
  type    = string
  default = "origin.invalid"
}

variable "github_repo" {
  type    = string
  default = "jhogunhee/logistics-back"
}

variable "github_branch" {
  type    = string
  default = "main"
}

# 값은 콘솔에서 만든 SecureString을 쓴다. Terraform이 관리하면 비밀번호가 state에 평문으로 남는다.
variable "db_password_param" {
  type    = string
  default = "/wareflow/db/password"
}

variable "log_retention_days" {
  type    = number
  default = 7
}
