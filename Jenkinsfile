#!groovy
def BN = (BRANCH_NAME == 'master' || BRANCH_NAME.startsWith('releases/')) ? BRANCH_NAME : 'releases/2024-06'

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
        upstream("knime-reporting/${env.BRANCH_NAME.replaceAll('/', '%2F')}" +
            ", knime-workbench/${env.BRANCH_NAME.replaceAll('/', '%2F')}")
    ]),
    parameters(workflowTests.getConfigurationsAsParameters()),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.personalproductivity')

    workflowTests.runTests(
        dependencies: [
            repositories: [
                "knime-productivity-oss", "knime-json", "knime-javasnippet", "knime-reporting", "knime-jep", 'knime-kerberos',
                "knime-filehandling", "knime-excel", "knime-ensembles", "knime-distance", "knime-js-core", "knime-js-base",
                "knime-server-client", "knime-com-shared", "knime-buildworkflows", "knime-gateway"
            ]
        ]
    )

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar()
    }
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}
/* vim: set shiftwidth=4 expandtab smarttab: */
