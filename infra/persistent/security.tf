data "aws_ec2_managed_prefix_list" "cloudfront_origin" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb-sg"
  description = "ALB - allow HTTP from CloudFront only"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${var.project}-alb-sg" }
}

resource "aws_security_group" "app" {
  name        = "${var.project}-app-sg"
  description = "App (Fargate) - allow 8080 from ALB only"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${var.project}-app-sg" }
}

resource "aws_security_group" "db" {
  name        = "${var.project}-db-sg"
  description = "RDS - allow PostgreSQL from app only"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${var.project}-db-sg" }
}

# ALB 주소를 알아도 CloudFront를 거치지 않으면 닿지 않는다.
resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from CloudFront edge"

  prefix_list_id = data.aws_ec2_managed_prefix_list.cloudfront_origin.id
  ip_protocol    = "tcp"
  from_port      = 80
  to_port        = 80
}

# 소스를 IP가 아니라 보안그룹으로 둔다. ALB 주소가 바뀌어도 규칙은 그대로 유효하다.
resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id = aws_security_group.app.id
  description       = "8080 from ALB"

  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id = aws_security_group.db.id
  description       = "PostgreSQL from app"

  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}

# ALB는 타겟으로, 앱은 ECR·CloudWatch·SSM·RDS로 먼저 나가야 한다.
resource "aws_vpc_security_group_egress_rule" "alb_all" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "db_all" {
  security_group_id = aws_security_group.db.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
