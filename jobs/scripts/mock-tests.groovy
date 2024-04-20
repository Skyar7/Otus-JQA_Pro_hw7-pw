timeout(15) {
    node("maven") {
        wrap([$class: 'BuildUser']) {
            currentBuild.description = """
build user: ${BUILD_USER}
branch: ${REFSPEC}
"""

            config = readYaml text: env.YAML_CONFIG ?: null;

            if (config != null) {
                for (param in config.entrySet()) {
                    env."${param.getKey()}" = param.getValue()
                }
            }
        }

        stage("Checkout") {
            checkout scm;
        }
        stage("Create configuration") {
        }
        stage("Run mock tests") {
            sh "mkdir ./reports"
            sh "docker run --rm --network=host --env-file ./.env -v ./reports:root/mock_tests_allure-results -t localhost:5005/mock_tests:${env.getProperty('TEST_VERSION')}"
        }
        stage("Publish allure results") {
            REPORT_DISABLE = Boolean.parseBoolean(env.getProperty('REPORT_DISABLE')) ?: false
            allure([
                    reportBuildPolicy: 'ALWAYS',
                    results: ["./reports", "./allure-results"],
                    disabled: REPORT_DISABLE
            ])
        }
    }
}