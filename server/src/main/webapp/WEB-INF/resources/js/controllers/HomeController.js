'use strict';

/**
 * HomeController
 * @constructor
 */

var HomeController = function($rootScope, $scope, $modal, $log, $location, balanceService, serverTransactionsFactory, userAccountFactory) {
	$scope.user = $rootScope.loggedUser;
	$scope.editMode = false;
	$scope.lastTransactions = [];
	$scope.allAdmins = [];
	$scope.homeObject = {
			balance: ""
	};
	
	loadRemoteData();

	function loadRemoteData(){
		balanceService.getBalance().then(function(balance){
			$scope.homeObject.balance = balance;
		});
		
		serverTransactionsFactory.getLastThreeTransactions().then(function(transactions){
			$scope.lastTransactions = transactions.data;
		});
		
		userAccountFactory.getAdmins().then(function(admins){
			$scope.allAdmins = admins; 
		});

	}

	$scope.openEmailModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalEmail.html',
			controller: ModalEmailInstanceController,
		});
		
		modalInstance.result.then(function(user){
			user.id = $rootScope.loggedUser.id;
			user.username = $rootScope.loggedUser.username;
			console.log(user);
			userAccountFactory.updateMail(user).then(function(){
				userAccountFactory.getLoggedUser($rootScope.loggedUser.username).then(function(loggedUser){
					$rootScope.loggedUser = loggedUser;
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
			user.id = $rootScope.loggedUser.id;
			user.username = $rootScope.loggedUser.username;
			userAccountFactory.updatePassword(user).then(function(){
				userAccountFactory.getLoggedUser($rootScope.loggedUser.username).then(function(loggedUser){
					$rootScope.loggedUser = loggedUser;
				});
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
			id: "",
			usernaem: "",
			new_email: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.user.new_email.length != 0){			
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
			id: "",
			username: "",
			old_password: "",
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