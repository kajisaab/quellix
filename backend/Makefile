GRADLE := ./gradlew
ENV_FILE ?= .env

IMAGE ?= quellix-backend
TAG ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo latest)
SERVER_PORT ?= 8080

.DEFAULT_GOAL := help
.PHONY: help run clean-build build test clean env docker-build docker-run

# Load environment variables.
define load_env
	@if [ ! -f $(ENV_FILE) ]; then \
		echo "ERROR: $(ENV_FILE) not found"; \
		exit 1; \
		fi
endef

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf " %-10s %s\n", $$1, $$2}'

env: ## Load the environment variables to run application
	$(call load_env)
	@echo ""
	@echo "Loaded environment configuration:"
	@grep -v '^[[[:space:]]*#' $(ENV_FILE) | grep -v '^[[:space:]]*$$'
	@echo ""

run: ## Start the Spring Boot app
	$(call load_env)
	@set -a; . ./$(ENV_FILE); set +a; \
		$(GRADLE) bootRun \
		--args='--spring.profiles.active=$${APP_ENV:-dev}'

clean-build: ## Build the project and refresh-dependencies
	$(GRADLE) build --refresh-dependencies -x test

build: ## Build the project and run tests.
	$(GRADLE) build

test: ## Run tests only
	$(GRADLE) test

clean: ## Delete build output
	$(GRADLE) clean

docker-build: ## Docker Build
	docker build --build-arg SERVER_PORT=$(SERVER_PORT) -t $(IMAGE):$(TAG) .

docker-run: ## Run the Docker image with the env file. 
	$(call load_env)
	docker run --rm --env-file $(ENV_FILE) -e SERVER_PORT=$(SERVER_PORT) -p $(SERVER_PORT) $(IMAGE):$(TAG);
