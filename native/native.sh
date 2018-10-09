#!/bin/sh
JAR=native-jar-with-dependencies.jar
BIN="`realpath $0`"
DIR="`dirname "$BIN"`"
exec java -jar "$DIR/$JAR"
