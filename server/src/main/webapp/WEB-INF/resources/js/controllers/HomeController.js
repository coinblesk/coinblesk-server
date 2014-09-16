'use strict';

/**
 * HomeController
 * @constructor
 */

var HomeController = function($rootScope, $scope, $modal, $log, $location, mainRequestFactory, userAccountFactory) {
//	$scope.user = $rootScope.loggedUser;
	$scope.editMode = false;
	$scope.balance = "";
	$scope.user = [];
	$scope.transactions = [];
	$scope.messages = [];

	
	loadRemoteData();

	function loadRemoteData(){
		mainRequestFactory.getMainRequestObjects().then(function(mainResponseObject){
			$scope.balance = mainResponseObject.data.balance;
			$scope.user = mainResponseObject.data.userModelObject;
			$scope.transactions = mainResponseObject.data.getHistoryTransferObject.transactionHistory;
			$scope.messages = mainResponseObject.data.getMessageTransferObject.messagesList;

		});

	}

	$scope.openEmailModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalEmail.html',
			controller: ModalEmailInstanceController,
		});
		
		modalInstance.result.then(function(user){
			console.log(user);
			user.username = $scope.user.username;
			userAccountFactory.updateMail(user).then(function(){
				userAccountFactory.getLoggedUser().then(function(loggedUser){
					$scope.user = loggedUser;
				});
			});
		});
	};
  
	  $scope.openPasswordModal = function () {
		  var modalPasswordInstance = $modal.open({
			  templateUrl: 'modalPassword.html',
			  controller: ModalPasswordInstanceController
		});
			
		modalPasswordInstance.result.then(function(user){
			user.username = $scope.user.username;
			console.log(user);
			userAccountFactory.updatePassword(user).then(function(){
				userAccountFactory.getLoggedUser().then(function(loggedUser){
					$scope.user = loggedUser;
				});
			});
		});
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

var ModalEmailInstanceController = function ($scope, $modalInstance) {
	
	$scope.user = {
			username: "",
			email: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.user.email.length != 0){			
			$modalInstance.close($scope.user);
		}else{
			$scope.setError("Field has to be filled out!");
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

var ModalPasswordInstanceController = function ($scope, $modalInstance) {

	$scope.user = {
			username: "",
			password: "",
			password_confirm: ""
	};
	
	$scope.submit = function() {
		if($scope.user.old_password.length != 0 || $scope.user.password.length != 0 || $scope.user.password_confirm.length != 0){			
			$modalInstance.close($scope.user);
		}else{
			$scope.setError("All fields have to be filled out!");
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