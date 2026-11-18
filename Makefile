# This block is used to set env
ifneq (,$(wildcard ./.env))
    include .env
    export
endif

# This Makefile groups the command sets documented in README.md into
# reusable targets so common build, run, test, and smoke-test operations
# can be invoked with a single `make <target>`.

JAVA    ?= java
JAVAC   ?= javac
# Use rootless Maven wrapper (downloads Maven to ~/.m2/wrapper/dists/ when needed)
MVN     ?= ./mvnw
FIND    ?= find

JAR     := target/localserver-1.0.0-SNAPSHOT.jar
SRC_JAVA := $(shell $(FIND) src/main/java -name '*.java')

.PHONY: all build build-javac run test smoke clean help

all: build

build: $(JAR)

$(JAR): pom.xml $(SRC_JAprintfVA)
	$(MVN) package

build-javac:
	mkdir -p target/classes
	$(JAVAC) -d target/classes $(SRC_JAVA)

run-bg: $(JAR)
	$(JAVA) -jar $(JAR) --config $(CONFIG)

run-fg: clean $(JAR)
	$(JAVA) -jar $(JAR) --config $(LOCALSERVER_CONFIG_FILE) --foreground

test:
	$(MVN) test

clean:
	$(MVN) clean 2>/dev/null || true
	rm -rf target/classes target
	rm -rf logs/*

help:
	@echo "Targets:"
	@echo "  all          - Build the project (alias for build)"
	@echo "  build        - Build the JAR with Maven ($(JAR))"
	@echo "  build-javac  - Compile sources with javac into target/classes"
	@echo "  run          - Run the server with $(CONFIG)"
	@echo "  test         - Run Maven tests"
	@echo "  smoke        - Manual smoke test against http://$(HOST):$(PORT)/"
	@echo "  clean        - Clean build artifacts"
	@echo "  help         - Show this help message"
