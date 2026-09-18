#!/usr/bin/env bash
# 켜기: ephemeral을 올려 ALB 주소를 얻고, 그 주소로 persistent의 CloudFront 오리진을 갱신한다.
#   사용법: ./up.sh <이미지 태그(커밋 SHA)>
#   예:     ./up.sh 1b64d0b5e3c7
set -euo pipefail

IMAGE_TAG="${1:?사용법: ./up.sh <이미지 태그(커밋 SHA)>}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 프로바이더가 700MB 남짓이다. 레포가 있는 드라이브가 작으면 TF_DATA_ROOT로 다른 곳에 둔다.
#   예: TF_DATA_ROOT=C:/Users/me/.terraform-data ./up.sh <SHA>
tf_data_dir() {
  if [ -n "${TF_DATA_ROOT:-}" ]; then
    export TF_DATA_DIR="${TF_DATA_ROOT}/$1"
  fi
}

echo "==> [1/3] ephemeral apply (RDS 스냅샷 복원 5~10분)"
tf_data_dir ephemeral
cd "$HERE/ephemeral"
terraform init -input=false >/dev/null
terraform apply -input=false -auto-approve -var "image_tag=${IMAGE_TAG}"

ALB_DNS="$(terraform output -raw alb_dns_name)"
echo "==> [2/3] ALB 주소: ${ALB_DNS}"

echo "==> [3/3] persistent apply (CloudFront 반영 5~15분)"
tf_data_dir persistent
cd "$HERE/persistent"
terraform init -input=false >/dev/null
terraform apply -input=false -auto-approve -var "alb_dns_name=${ALB_DNS}"

CF_DOMAIN="$(terraform output -raw cloudfront_domain_name)"
echo
echo "완료. 스모크 테스트:"
echo "  curl -i https://${CF_DOMAIN}/health          # 204"
echo "  로그인 + 목록 조회 1건까지 확인할 것 (/health는 DB를 보지 않는다)"
