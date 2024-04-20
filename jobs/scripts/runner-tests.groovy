import groovy.json.JsonSlurperClassic

timeout(60) {
    node('maven') {
        wrap([$class: 'BuildUser']) {
            currentBuild.description = """
build user: ${BUILD_USER}
branch: ${REFSPEC}
"""
//            BUILD_USER_EMAIL = $BUILD_USER_EMAIL;
            config = readYaml text: env.YAML_CONFIG ?: null;

            if (config != null) {
                for (param in config.entrySet()) {
                    env."${param.getKey()}" = param.getValue()
                }
            }

            echo "TEST_TYPES: ${env.TEST_TYPES}"
            String testTypesString = env.TEST_TYPES.replace("[", "").replace("]","").replace("\"", "")
            testTypes = testTypesString.split(",\\s*")
            echo "Processed testTypes: ${testTypes}"
        }

        stage('Checkout') {
            checkout scm;
            // git branch: "$REFSPEC", credentialsId: 'jenkins', url: 'git@github.com:Skyar7/Otus-JQA_Pro_hw7-pw.git'
        }

        def jobs = [:];
        def triggeredJobs = [:];

        for (type in testTypes) {
            // Создаем замыкание с явным указанием контекста, используя другое имя для параметра
            def jobClosure = { testType ->
                node("maven") {
                    stage("Running $testType tests") {
                        triggeredJobs[testType] = build(job: "$testType-tests", parameters: [
                                text(name: "YAML_CONFIG", value: env.YAML_CONFIG)
                        ])
                    }
                }
            }.curry(type) // Используем метод curry для передачи текущего значения type в замыкание

            jobs[type] = jobClosure
        }

        parallel jobs;

        // Ожидаем завершения всех задач
        waitUntil {
            // Проверяем, завершились ли все задачи
            return triggeredJobs.every { it.value.result != null }
        }

        stage("Creating additional report artifacts") {
            dir("allure-results") {
                sh "echo BROWSER_NAME=${env.getProperty('BROWSER_NAME')} > environments.txt"
                sh "echo BROWSER_VERSION=${env.getProperty('BROWSER_VERSION')} >> environments.txt"
                sh "echo TEST_VERSION=${env.getProperty('TEST_VERSION')} >> environments.txt"
            }
        }

        stage("Copy allure reports") {
            dir("allure-results") {

                for(type in testTypes) {
                    copyArtifacts filter: "allure-report.zip", projectName: "${triggeredJobs[type].projectName}", selector: lastSuccessful(), optional: true
                    sh "unzip ./allure-report.zip -d ."
                    sh "rm -rf ./allure-report.zip"
                }
            }
        }

        stage("Publish allure results") {
            REPORT_DISABLE = Boolean.parseBoolean(env.getProperty('REPORT_DISABLE')) ?: false
            allure([
                    reportBuildPolicy: 'ALWAYS',
                    results: ["."],
                    disabled: REPORT_DISABLE
            ])
        }

        stage("Send Telegram notification") {
            def message = "🔹🔹🔹🔹 Tests Result 🔹🔹🔹🔹\n";
            message += "Tests running: ${String.join(", ", jobs.setKeys())}";
            message += "BRANCH: ${REFSPEC}\n";
            message += "TEST_VERSION: ${TEST_VERSION}\n";

            def slurper = new JsonSlurperClassic().parseTest("./allure-results/widgets/summary.json")

            if((slurper['failed'] as Integer) > 0) {
                message += "Status: FAILED ❌";
                message += "\n@Skyar7";
//                message += "\n@${BUILD_USER_EMAIL}";
            } else if ((slurper['skipped'] as Integer) > 0 && (slurper['TOTAL'] as Integer) == 0) {
                message += "Status: SKIPPED ⚠";
            } else {
                message += "Status: PASSED ✅";
            }

            def url = "https://api.telegram.org/bot6417628982:AAHTx9w923pwYynFdObHIKvax_VHxspRDj4/";
//            withCredentials([string(credentialsId: telegram_token, valueVar: "TELEGRAM_TOKEN")]);
//            def url = "https://api.telegram.org/bot${TELEGRAM_TOKEN}";
            def stringBuilder = new StringBuilder(url);
            def urlConnection = new URL(stringBuilder.toString()).openConnection() as HttpURLConnection;
            urlConnection.setRequestMethod('GET');
            urlConnection.setDoOutput(true);
        }
    }
}