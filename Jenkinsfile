#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
	pipelineTriggers([upstream('knime-base/' + env.BRANCH_NAME.replaceAll('/', '%2F'))]),
	pipelineTriggers([upstream('knime-jenkins/' + env.BRANCH_NAME.replaceAll('/', '%2F'))]),
	pipelineTriggers([upstream('knime-reporting/' + env.BRANCH_NAME.replaceAll('/', '%2F'))]),
	pipelineTriggers([upstream('knime-workbench/' + env.BRANCH_NAME.replaceAll('/', '%2F'))]),
	buildDiscarder(logRotator(numToKeepStr: '5')),
	disableConcurrentBuilds()
])

try {
	knimetools.defaultTychoBuild('org.knime.update.personalproductivity')

	workflowTests.runTests(
		dependencies: [
			repositories: [ "knime-productivity-oss", "knime-json", "knime-javasnippet", "knime-reporting", "knime-jep", "knime-filehandling", "knime-excel", "knime-ensembles", "knime-distance" ]
		]
	)

	stage('Sonarqube analysis') {
		env.lastStage = env.STAGE_NAME
	 	workflowTests.runSonar()
	}
 } catch (ex) {
	 currentBuild.result = 'FAILED'
	 throw ex
 } finally {
	 notifications.notifyBuild(currentBuild.result);
 }
/* vim: set ts=4: */
