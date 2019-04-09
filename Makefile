MAJOR ?= 0
MINOR ?= 2

jar_file := fonda.jar

all: help

help: ##@other Show this help
	@perl -e '$(HELP_FUN)' $(MAKEFILE_LIST)

# This is a code for automatic help generator.
# It supports ANSI colors and categories.
# To add new item into help output, simply add comments
# starting with '##'. To add category, use @category.
GREEN  := $(shell tput -Txterm setaf 2)
WHITE  := $(shell tput -Txterm setaf 7)
YELLOW := $(shell tput -Txterm setaf 3)
RESET  := $(shell tput -Txterm sgr0)
HELP_FUN = \
		   %help; \
		   while(<>) { push @{$$help{$$2 // 'options'}}, [$$1, $$3] if /^([a-zA-Z\-]+)\s*:.*\#\#(?:@([a-zA-Z\-]+))?\s(.*)$$/ }; \
		   print "Usage: make [target]\n\n"; \
		   for (sort keys %help) { \
			   print "${WHITE}$$_:${RESET}\n"; \
			   for (@{$$help{$$_}}) { \
				   $$sep = " " x (32 - length $$_->[0]); \
				   print "  ${YELLOW}$$_->[0]${RESET}$$sep${GREEN}$$_->[1]${RESET}\n"; \
			   }; \
			   print "\n"; \
		   }

.PHONY: repl next-version prepare-release

shadow_pid_file := .shadow-cljs/server.pid
tag_prefix := v
next_version_file := NEXT_VERSION
version_file := VERSION
current_version := ${MAJOR}.${MINOR}

# this regex only matches the previos major.minor version, git-revision will
# compute the patch.
tag_regex := ${tag_prefix}${current_version}.0

next_revision = $(shell ./scripts/git-revision --regex ${tag_regex})
next_version := $(join ${current_version}, .${next_revision}${snapshot_string})
next_tag := ${tag_prefix}${next_version}

clean: ##@dev Clean all the outputs.
	rm -rf ${jar_file} extra .cljs

next-version: ##@dev Output the next computed version.
	@echo ${next_version}

${next_version_file}:
# This does not work - I was trying to detect shell failures
# ifneq (${.SHELLSTATUS},0)
# 	@echo "Failure in git-revision: ${next_revision}"
# 	@exit 1
# endif
	@echo ${next_version} > ${next_version_file}

${shadow_pid_file}:
	clojure -A:dev -m shadow.cljs.devtools.cli watch repl

watch: ${shadow_pid_file} ##@dev Launch the REPL.

pom.xml: deps.edn
	clojure -Srepro -Spom

extra/META-INF/:
	mkdir -p "$@"

extra/META-INF/pom.xml: pom.xml extra/META-INF/
	cp pom.xml "$@"

${jar_file}: deps.edn src/**/* extra/META-INF/pom.xml
	clojure -A:pack -m mach.pack.alpha.skinny --no-libs -e "extra" --project-path "$@"

jar: ${jar_file} ##@build Package the code in a jar file.

prepare-release: ##@deploy Prepare a release - bump version, commit and tag.
	sed -i.old 's/\"${current_version}.*\"/\"${next_version}\"/g' README.md
	lein change version set \"${next_version}\"
	mvn -B versions:set -DgenerateBackupPoms=false -DnewVersion=${next_version}
	git add project.clj pom.xml README.md
	git commit --gpg-sign --message "Release ${next_version}"
	git tag -sa --message "Release ${next_tag}" ${next_tag}

deploy: clean jar  ##@deploy Deploy the fonda jar file.
	mvn clean deploy:deploy-file \
      -Durl=https://repo.clojars.org \
      -DrepositoryId=clojars \
      -DpomFile=pom.xml \
      -Dfile=${jar_file}
	git push --follow-tags
