#!/bin/sh
mvn install:install-file -Dfile=lib/org.eclipse.cdt.core_6.5.0.201807181141.jar -DgroupId=org.eclipse.cdt.core -DartifactId=cdtcore -Dversion=6.5.0 -Dpackaging=jar
