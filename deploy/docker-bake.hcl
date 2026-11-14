group "default" {
  targets = ["mop-server", "mop-web"]
}

target "mop-server" {
  context    = "."
  dockerfile = "deploy/docker/server.Dockerfile"
  tags       = ["mop-server:dev"]
}

target "mop-web" {
  context    = "."
  dockerfile = "deploy/docker/web.Dockerfile"
  tags       = ["mop-web:dev"]
}
