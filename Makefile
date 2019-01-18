.PHONY: jar clean

all: jar

LOCAL_JAR_FILE=fonda.jar

clean:
	rm -rf ${LOCAL_JAR_FILE} "extra/"

pom.xml: deps.edn
	clojure -Srepro -Spom

extra/META-INF/:
	mkdir -p "$@"

extra/META-INF/pom.xml: pom.xml extra/META-INF/
	cp pom.xml "$@"

${LOCAL_JAR_FILE}: deps.edn src/**/* extra/META-INF/pom.xml
	clojure -A:pack -m mach.pack.alpha.skinny --no-libs -e "extra" --project-path "$@"

jar: ${LOCAL_JAR_FILE}
