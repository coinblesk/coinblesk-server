'use strict';

/**
 * UsersController
 * @constructor
 */
var UsersController = function($rootScope, $scope, $location, $modal, userAccountFactory) {
	$scope.users = {};
	$scope.editMode = false;
	$scope.allUsers = [];
	
	loadRemoteData();
	
	function loadRemoteData(){
		userAccountFactory.getUsers()
		.then(
				function(users){
					$scope.allUsers = users.userAccountObjectList; 
				});
	}
	
	$scope.openEmailToAllModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalEmailToAll.html',
			controller: ModalEmailToAllInstanceController,
		});
		
		modalInstance.result.then(function(emailContent){
			console.log(emailContent);
			userAccountFactory.sendMailToAll(emailContent).then(function(){
				
			});
		});
	};
};

var ModalEmailToAllInstanceController = function ($scope, $modalInstance) {
	
	$scope.emailContent = {
			subject: "",
			text: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.emailContent.subject.length != 0 && $scope.emailContent.text.length != 0){			
			$modalInstance.close($scope.emailContent);
		}else{
			$scope.setError("Fields have to be filled out!");
		}
	};

	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
    $scope.resetError = function() {
        $scope.error = false;
        $scope.errorMessage = '';
    };

    $scope.setError = function(message) {
        $scope.error = true;
        $scope.errorMessage = message;
    };
};