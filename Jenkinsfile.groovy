def project_token = 'abcdefghijklmnopqrstuvwxyz0123456789ABCDEF'
def version = '1.0'
def APP_NAME = 'myapp2'
// Reference the GitLab connection name from your Jenkins Global configuration (https://JENKINS_URL/configure, GitLab section)
properties([
    gitLabConnection('your-gitlab-connection-name'),
    pipelineTriggers([
        [
            $class: 'GitLabPushTrigger',
            branchFilterType: 'All',
            triggerOnPush: true,
            triggerOnMergeRequest: true,
            triggerOpenMergeRequestOnPush: "never",
            triggerOnNoteRequest: true,
            noteRegex: "Jenkins please retry a build",
            skipWorkInProgressMergeRequest: true,
            secretToken: project_token,
            ciSkip: false,
            setBuildDescription: true,
            addNoteOnMergeRequest: true,
            addCiMessage: true,
            addVoteOnMergeRequest: true,
            acceptMergeRequestOnSuccess: true,
            branchFilterType: "NameBasedFilter",
            includeBranchesSpec: "",
            excludeBranchesSpec: "",
        ]
    ])
])


node() {
    try {

        def buildNum = env.BUILD_NUMBER
        def branchName= env.BRANCH_NAME
	print branchName	
	print buildNum	
        
        /*Récupération du dépôt git applicatif */
    	stage('SERVICE - Git checkout'){
      	git branch: branchName, url: "https://github.com/jihedjarry/myapp2.git"
	}


	/* Récupération du commitID long */
    	def commitIdLong = sh returnStdout: true, script: 'git rev-parse HEAD'

    	/* Récupération du commitID court */
    	def commitId = commitIdLong.take(7)

	print """
     	#################################################
        	BanchName: $branchName
        	CommitID: $commitId
        	AppVersion: $version
        	JobNumber: $buildNum
     	#################################################
        	"""
	
	/*Création de l'image */
        stage('build et run'){
        sh '''
		docker build -t myapp:1.0 .
		docker run -d -p 80:5000 --name myapp myapp:1.0
		sleep 5s
	'''
        }
	
	/*Test*/
	stage('Test'){
	sh '''
		curl http://localhost
		docker stop myapp
		docker rm myapp
		docker rmi myapp:1.0	
	''' 
        }

	def imageName='192.168.222.176:5000/myapp'
    	stage('DOCKER - Build/Push registry'){
      	docker.withRegistry('http://192.168.222.176:5000', 'myregistry_login') {
		def customImage = docker.build("$imageName:${version}-${commitId}")
        	customImage.push()
 		}
      	sh "docker rmi $imageName:${version}-${commitId}"
    	}

	/* Docker - test */
    	stage('DOCKER - check registry'){
      	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'myregistry_login',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
      	sh 'curl -sk --user $USERNAME:$PASSWORD https://192.168.222.176:5000/v2/myapp/tags/list'
      	 }
    	}
    } finally {
        cleanWs()
    }
}












