# 쿠키가 Secure라 API도 HTTPS여야 한다. ALB에 인증서를 붙이려면 소유 도메인 검증이 필요해서,
# 도메인 없이 HTTPS를 얻는 길로 CloudFront를 둔다. 만들고 지우는 데 각각 15분이 넘어 상시 스택에 남긴다.

# 기본 캐시 정책은 응답을 캐시하고 쿠키·헤더를 떼어낸다. 세션과 CORS가 통째로 깨진다.
data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_distribution" "api" {
  enabled     = true
  comment     = "${var.project} API entry (HTTPS)"
  price_class = "PriceClass_All"

  origin {
    origin_id   = "alb"
    domain_name = var.alb_dns_name

    custom_origin_config {
      origin_protocol_policy = "http-only"
      http_port              = 80
      https_port             = 443
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "alb"
    viewer_protocol_policy = "redirect-to-https"

    # allowCredentials(true)라 프리플라이트가 반드시 온다. OPTIONS가 빠지면 브라우저 요청이 전부 막힌다.
    allowed_methods = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods  = ["GET", "HEAD"]

    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
    compress                 = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
