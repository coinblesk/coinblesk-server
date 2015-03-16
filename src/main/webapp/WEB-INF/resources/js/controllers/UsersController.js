'use strict';

/**
 * UsersController
 * @constructor
 */
var UsersController = function($rootScope, $scope, $location, $modal, userAccountFactory) {
	$scope.users = {};
	$scope.roles = {};
	$scope.editMode = false;
	$scope.allUsers = [];
	$scope.allAdmins = [];
	$scope.roles = {
		'1':'User',
		'2':'Admin',
		'3':'User/Admin'
	};
	
	$scope.Split = function(string, nb) {
	    $scope.array = string.split('@');
	    return $scope.result = $scope.array[nb];
	};
	
	loadRemoteData();
	
	function loadRemoteData(){
		$rootScope.initialized = true;
		userAccountFactory.getUsers()
		.then(
				function(users){
					$scope.allUsers = users.userAccountObjectList;
					getAllAdmins();
				});
	}
	
	function getAllAdmins(){
		var arrayAdmins = [];
		for(var i = 0; i < $scope.allUsers.length; i++){
			var admin = $scope.allUsers[i];
			if(admin.role == 2 || admin.role == 3){
				arrayAdmins.push(admin);
			}
		}
		$scope.allAdmins = arrayAdmins;
	}
	
	$scope.openEmailToAllModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalEmailToAll.html',
			controller: ModalEmailToAllInstanceController,
		});
		
		modalInstance.result.then(function(mail){
			console.log(mail);
			userAccountFactory.sendMailToAll(mail).then(function(){
				
			});
		});
	};
	
	 $scope.openAdminsModal = function () {
		 var modalAdminsInstance = $modal.open({
			 templateUrl: 'modalInviteAdmin.html',
			 controller: ModalAdminsInstanceController
		  });
		  
		 modalAdminsInstance.result.then(function(user){
			 $scope.userAdmins = user;
			 if(!_.include($scope.allAdmins, user.email)){
				 userAccountFactory.inviteAdmin(user).then(function(){ 
				 });
			 }else{
				 $scope.setError("The email address " + user.email + " is already an admin!");
			 }
		  });
	  
	  };
};

var ModalEmailToAllInstanceController = function ($scope, $modalInstance) {
	
	$scope.mail = {
			subject: "",
			mail: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.mail.subject.length != 0 && $scope.mail.message.length != 0){			
			$modalInstance.close($scope.mail);
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

var ModalAdminsInstanceController = function ($rootScope, $scope, $modalInstance) {
	
	$scope.user = {
			email: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.user.email.length != 0){
			$modalInstance.close($scope.user);
		}else{
			$scope.setError("field has to be filled out!");
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