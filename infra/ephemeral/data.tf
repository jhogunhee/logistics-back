# 상시 스택의 출력값을 읽는다. 반대 방향(상시가 이 스택을 읽는 것)은 하지 않는다 —
# 서로 읽게 두면 이 스택을 destroy한 뒤 상시 스택을 plan할 수 없게 된다.
data "terraform_remote_state" "persistent" {
  backend = "s3"

  config = {
    bucket = "wms-tfstate-721274598949"
    key    = "wareflow/persistent.tfstate"
    region = "ap-northeast-2"
  }
}

data "aws_caller_identity" "current" {}

locals {
  p = data.terraform_remote_state.persistent.outputs

  db_password_param_arn = "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.p.db_password_param}"
}
