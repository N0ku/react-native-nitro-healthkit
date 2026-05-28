.PHONY: start publish clean help install-packages install-example build-packages nitrogen pod-install run-ios run-android test test-android test-ts

# Colors for messages
GREEN := \033[0;32m
BLUE := \033[0;34m
YELLOW := \033[0;33m
NC := \033[0m # No Color

help: ## Show this help
	@echo "$(BLUE)react-native-nitro-healthkit - Available commands:$(NC)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-15s$(NC) %s\n", $$1, $$2}'

start: ## Run the full project (build + run iOS)
	@echo "$(BLUE)🚀 Starting react-native-nitro-healthkit...$(NC)"
	@$(MAKE) install-packages
	@$(MAKE) build-packages
	@$(MAKE) nitrogen
	@$(MAKE) install-example
	@$(MAKE) pod-install
	@$(MAKE) run-ios

install-packages: ## Install the package dependencies
	@echo "$(YELLOW)📦 Installing package dependencies...$(NC)"
	cd packages && npm install

build-packages: ## Build the TypeScript package
	@echo "$(YELLOW)🔨 Building the package...$(NC)"
	cd packages && npm run build

nitrogen: ## Generate the Nitrogen artefacts
	@echo "$(YELLOW)⚡ Generating Nitrogen artefacts...$(NC)"
	cd packages && npx nitrogen

install-example: ## Install the example app dependencies
	@echo "$(YELLOW)📦 Installing example app dependencies...$(NC)"
	cd example/my-app && npm install

pod-install: ## Install the CocoaPods
	@echo "$(YELLOW)🍎 Installing iOS pods...$(NC)"
	cd example/my-app/ios && pod install

run-ios: ## Run the iOS app
	@echo "$(GREEN)▶️  Running the iOS app...$(NC)"
	cd example/my-app && npx expo run:ios

run-android: ## Run the Android app (requires Health Connect installed)
	@echo "$(GREEN)▶️  Running the Android app...$(NC)"
	cd example/my-app && npx expo run:android

test: test-ts test-android ## Run all test suites (TS + Kotlin)

test-ts: ## Run the package TypeScript tests
	@echo "$(YELLOW)🧪 TypeScript tests...$(NC)"
	cd packages && npm test

test-android: ## Run the Android module Kotlin tests (gradle)
	@echo "$(YELLOW)🧪 Android Kotlin tests...$(NC)"
	cd example/my-app/android && ./gradlew :react-native-nitro-healthkit:test

clean: ## Clean build artefacts
	@echo "$(YELLOW)🧹 Cleaning build artefacts...$(NC)"
	cd packages && npm run clean
	rm -rf example/my-app/node_modules
	rm -rf example/my-app/ios/Pods
	rm -rf example/my-app/ios/build
	@echo "$(GREEN)✅ Clean complete$(NC)"

publish: ## Publish the new version to npm
	@echo "$(BLUE)📦 Publishing the new version...$(NC)"
	@$(MAKE) check-git-clean
	@$(MAKE) version-bump
	@$(MAKE) install-packages
	@$(MAKE) build-packages
	@$(MAKE) nitrogen
	@$(MAKE) git-commit-version
	@$(MAKE) npm-publish
	@echo "$(GREEN)✅ Publish completed successfully!$(NC)"

check-git-clean: ## Check that the repo is clean
	@echo "$(YELLOW)🔍 Checking repository state...$(NC)"
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "$(YELLOW)⚠️  There are uncommitted changes. Continue? (y/n)$(NC)"; \
		read -r answer; \
		if [ "$$answer" != "y" ]; then \
			echo "$(YELLOW)❌ Publish cancelled$(NC)"; \
			exit 1; \
		fi; \
	fi

version-bump: ## Bump the package version
	@echo "$(YELLOW)📝 Updating version...$(NC)"
	@echo "Which version do you want to publish? (patch/minor/major)"
	@read -r version_type; \
	cd packages && npm version $$version_type --no-git-tag-version; \
	NEW_VERSION=$$(node -p "require('./package.json').version"); \
	echo "$(GREEN)✅ Version updated: $$NEW_VERSION$(NC)"

git-commit-version: ## Commit the new version
	@echo "$(YELLOW)📝 Committing the new version...$(NC)"
	@NEW_VERSION=$$(cd packages && node -p "require('./package.json').version"); \
	git add packages/package.json packages/package-lock.json 2>/dev/null || true; \
	git commit -m "chore: bump version to $$NEW_VERSION" || true; \
	git tag "v$$NEW_VERSION"; \
	echo "$(GREEN)✅ Version $$NEW_VERSION committed and tagged$(NC)"

npm-publish: ## Publish the package to npm
	@echo "$(YELLOW)🚀 Publishing the package...$(NC)"
	cd packages && npm publish
	@NEW_VERSION=$$(cd packages && node -p "require('./package.json').version"); \
	echo "$(GREEN)✅ Package react-native-nitro-healthkit@$$NEW_VERSION published!$(NC)"

# Quick development commands
dev: install-packages build-packages nitrogen ## Quick development setup
	@echo "$(GREEN)✅ Development setup complete$(NC)"

rebuild: clean start ## Clean and rebuild everything
	@echo "$(GREEN)✅ Full rebuild complete$(NC)"
