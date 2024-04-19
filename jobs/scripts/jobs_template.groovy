timeout(60) {
   node('maven') {
      stage("Checkout") {
         checkout scm
      }
      stage("Deploy changes to jenkins") {
         sh "jenkins-jobs --conf ${WORKSPACE}/jobs/conf/jenkins-job-builder.ini update ./jobs"
      }
  }
}