resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "${var.project}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.project}-igw" }
}

resource "aws_subnet" "public" {
  for_each = var.public_subnets

  vpc_id            = aws_vpc.this.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az

  # 공인 IP는 ECS 서비스 쪽에서 태스크에만 준다. 서브넷 기본값으로 켜면 다른 리소스까지 IPv4 요금이 붙는다.
  map_public_ip_on_launch = false

  tags = { Name = "${var.project}-public-${each.key}" }
}

resource "aws_subnet" "db" {
  for_each = var.db_subnets

  vpc_id            = aws_vpc.this.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az

  tags = { Name = "${var.project}-db-${each.key}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = { Name = "${var.project}-rtb-public" }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

# 밖으로 나가는 경로가 없다. NAT가 없으므로 AZ별로 나눌 이유도 없어 하나만 둔다.
resource "aws_route_table" "db" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.project}-rtb-db" }
}

resource "aws_route_table_association" "db" {
  for_each = aws_subnet.db

  subnet_id      = each.value.id
  route_table_id = aws_route_table.db.id
}

resource "aws_db_subnet_group" "db" {
  name       = "${var.project}-db-subnet-group"
  subnet_ids = [for s in aws_subnet.db : s.id]

  tags = { Name = "${var.project}-db-subnet-group" }
}
