library 'ci-libs'

def call(Map pipelineParams) {

podTemplate(yaml: """
kind: Pod
metadata:
  name: egov-deployer
spec:
  containers:
  - name: egov-deployer
    image: egovio/egov-deployer:3-master-931c51ff
    command:
    - cat
    tty: true
    env:  
      - name: "GOOGLE_APPLICATION_CREDENTIALS"
        value: "/var/run/secret/cloud.google.com/service-account.json"              
    volumeMounts:
      - name: kube-config
        mountPath: /root/.kube     
    resources:
      requests:
        memory: "256Mi"
        cpu: "200m"
      limits:
        memory: "256Mi"
        cpu: "200m"  
  volumes:   
  - name: kube-config
    secret:
        secretName: "${pipelineParams.environment}-kube-config"                    
"""
    ) {
        node(POD_LABEL) {
            try {
                git url: pipelineParams.repo, branch: pipelineParams.branch, credentialsId: 'git_read'
                stage('Deploy Images') {
                    container(name: 'egov-deployer', shell: '/bin/sh') {
                        sh """
                            /opt/egov/egov-deployer deploy --helm-dir `pwd`/${pipelineParams.helmDir} -c=${env.CLUSTER_CONFIGS}  -e ${pipelineParams.environment} "${env.IMAGES}"
                        """
                    }
                }    
                currentBuild.result = 'SUCCESS' // Mark the build as successful
            } catch (Exception e) {
                currentBuild.result = 'FAILURE' // Mark the build as failed
                throw e // Re-throw the exception
            } finally {
                // Slack notification based on the build result
                if (currentBuild.result == 'SUCCESS') {
                    slackSend (
                        botUser: true,
                        color: 'good', // green color for success
                        message: "Deployment successful for ${env.Images} services in ${pipelineParams.environment} environment! :tada:",
                        channel: '#e-gov',
                        teamDomain: 'westminister',
                        tokenCredentialId: 'slack-bot-token'
                    )
                    emailext body: "Deployment successful for ${env.Images} services in ${pipelineParams.environment} environment! 🎉", subject: "Successful Deployment: ${env.Images.split(':')[0]} in ${pipelineParams.environment}", to: 'ibrahim@westminister.tech,cc:godwyn@westminister.tech,cc:raymond@westminister.tech,cc:devops.consulting@ongraph.com'
                } else {
                    slackSend (
                        botUser: true,
                        color: 'danger', // red color for failure
                        message: "Deployment failed for ${env.Images} services in ${pipelineParams.environment} environment! :disappointed:\nPlease refer to this URL for more details: ${BUILD_URL}",
                        channel: '#e-gov',
                        teamDomain: 'westminister',
                        tokenCredentialId: 'slack-bot-token'
                    )
                    emailext body: "Deployment failed for ${env.Images} services in ${pipelineParams.environment} environment! 😞\nPlease refer to this URL for more details: ${BUILD_URL}/console", subject: "Failed Deployment: ${env.Images.split(':')[0]} in ${pipelineParams.environment}", to: 'ibrahim@westminister.tech,cc:godwyn@westminister.tech,cc:raymond@westminister.tech,cc:devops.consulting@ongraph.com'
                }
            }
        }
    }
}
