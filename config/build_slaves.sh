#!/bin/bash

SLAVES_DIR=$PWD
REGISTRY_URL=localhost:5005/jenkins_slave

for dockerfile in "$SLAVES_DIR"/slaves/*
do
  TAG=`echo $dockerfile | grep -oP "[a-z]+$"`
  docker build -f $dockerfile -t $REGISTRY_URL/$TAG .
  docker push $REGISTRY_URL/$TAG
done

tini -- /usr/local/bin/jenkins.sh "$@"
