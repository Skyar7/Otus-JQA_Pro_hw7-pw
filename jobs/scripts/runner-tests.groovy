import com.google.inject.Stage
import groovy.json.JsonSlurperClassic
import org.apache.groovy.io.StringBuilderWriter

timeout(30) {
    node("maven") {
        wrap([$class: 'BuildUser']) {
            currentBuild.description = """
user: $BUILD_USER
branch: $REFSPEC
"""
        }

        config = readYaml text: env.YAML_CONFIG

        if (config != null) {
            for(param in config.entrySet()) {
                env.setProperty(param.getKey(), param.getValue())
            }
        }

        echo "TEST_TYPES: ${env.TEST_TYPES}"
        String testTypesString = env.TEST_TYPES.replace("[", "").replace("]","").replace("\"", "")
        testTypes = testTypesString.split(",\\s*")
        echo "Processed testTypes: ${testTypes}"

        def triggerdJobs = [:]

        for (i = 0; i < testTypes.size(); i+= 1){
            def type = testTypes[i] + "-tests"
            sh "echo add ${type}"
            triggerdJobs[type]={
                build wait: true, job: type, parameters: [text(name: "YAML_CONFIG", value: env.YAML_CONFIG)]
            }
        }

        parallel triggerdJobs

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