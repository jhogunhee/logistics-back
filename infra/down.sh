#!/usr/bin/env bash
# 끄기: ephemeral만 지운다.
#   persistent는 건드리지 않는다. CloudFront가 사라진 ALB를 가리켜 502를 내도 상관없다 — 보는 사람이 없다.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# up.sh와 같은 규칙 — 레포가 있는 드라이브가 작으면 TF_DATA_ROOT를 쓴다.
if [ -n "${TF_DATA_ROOT:-}" ]; then
  export TF_DATA_DIR="${TF_DATA_ROOT}/ephemeral"
fi

cd "$HERE/ephemeral"
terraform init -input=false >/dev/null

# image_tag는 destroy에 쓰이지 않지만 변수 선언상 값이 필요하다.
terraform destroy -input=false -auto-approve -var "image_tag=unused"

echo
echo "완료. 시간당 과금되는 리소스(RDS·ALB·Fargate)가 모두 사라졌다."
echo "남는 것: VPC·ECR·ECS 클러스터·IAM·CloudFront (합쳐 월 \$1 미만)"
