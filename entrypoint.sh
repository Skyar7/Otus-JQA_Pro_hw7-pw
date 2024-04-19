#!/bin/bash

export JENKINS_HOSTNAME=$JENKINS_HOSTNAME
export JENKINS_USERNAME=$JENKINS_USERNAME
export JENKINS_PASSWORD=$JENKINS_PASSWORD

python3 conf/create_conf.py

cat conf/jenkins-job-builder.ini

jenkins-jobs --conf conf/jenkins-job-builder.ini update ./
