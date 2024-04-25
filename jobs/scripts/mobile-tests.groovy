timeout(15) {
    node("maven") {
        wrap([$class: 'BuildUser']) {
            currentBuild.description = """
build user: ${BUILD_USER}
branch: ${REFSPEC}
"""

            config = readYaml text: env.YAML_CONFIG

            if (config != null) {
                for (param in config.entrySet()) {
                    env.setProperty(param.getKey(), param.getValue())
                }
            }
        }

        stage("Checkout") {
            checkout scm;
        }

        stage("Create configuration") {
            sh "echo DEVICE_NAME=${env.getProperty('DEVICE_NAME')} > ./.env"
            sh "echo PLATFORM_NAME=${env.getProperty('PLATFORM_NAME')} >> ./.env"
            sh "echo PLATFORM_VERSION=${env.getProperty('PLATFORM_VERSION')} >> ./.env"
            sh "echo REMOTE_URL=${env.getProperty('REMOTE_URL')} >> ./.env"
        }

        stage("Mobile tests in docker image") {
            sh "docker run --rm \
            --network=host --env-file ./.env \
            -v /root/.m2/repository:/root/.m2/repository \
            -v ./surefire-reports:/home/ubuntu/mobile_tests/target/surefire-reports \
            -v ./allure-results:/home/ubuntu/mobile_tests/target/allure-results \
            -t localhost:5005/mobile_tests:${env.getProperty('TEST_VERSION')}"
        }

        stage("Publish Allure Reports") {
            allure([
                    includeProperties: false,
                    jdk: '',
                    properties: [],
                    reportBuildPolicy: 'ALWAYS',
                    results: [[path: './allure-results']]
            ])
        }
    }
}