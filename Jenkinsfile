#!groovy

library "knime-pipeline@$BRANCH_NAME"

def upstreamProject = 'org.knime.update.analytics-platform'

node {
	def upstreamParams = defaultProperties(upstreamProject)
	
	stage('Clean workspace') {
		cleanWorkspace()
	}
	
	stage('Checkout sources') {
		checkoutSources (
			defaultBranch: BRANCH_NAME,
			credentialsId: 'bitbucket-jenkins',
			repos: [
				[name : 'knime-config'],
				[name : 'knime-jenkins'],
				[name : 'knime-productivity'],
				[name : 'knime-shared'],
			]
		)
	}
	
	
	stage('Build update site') {
		buckminster (
			component: 'com.knime.update.productivity',
			baseline: [file: 'git/knime-config/org.knime.config/API-Baseline.target', name: 'Release 3.3'],
			xmlBeans: true,
			repos: ["$JENKINS_URL/jobs/${upstreamParams.p2}"]
		)
		
		finalizeP2Repository()
	}
	
	stage('Archive artifacts') {
		archive()
	}
}
