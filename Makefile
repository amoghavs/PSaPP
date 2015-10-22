DOXYGEN = doxygen

VERSION = 1
SUBDIRS = util stats dbase data recv send pred gui vis chck web sim greenqueue cfg

.PHONY: subdirs $(SUBDIRS)

subdirs: $(SUBDIRS)

$(SUBDIRS):
	$(MAKE) -C $@

doxygen:
	$(DOXYGEN) doxygen.cfg

clean:
	$(foreach d,$(SUBDIRS),$(MAKE) -C $(d) clean;)
	rm -f ./lib/PSaPP.jar

# this target comprises just the components needed for a remote build (one without a backing database)

dist: subdirs
	(cd ..; jar -J-Xms16m -J-Xmx1024m cvf ./PSaPP/lib/PSaPP.jar `find PSaPP -name "*.class" -print`; chmod a+r ./PSaPP/lib/PSaPP.jar)
