'use strict';

/**
 * HomeController
 * @constructor
 */

var HomeController = function($scope, $modal, $log, $location, $rootScope, balanceService, serverTransactionsFactory, userAccountFactory) {
	$scope.home = {};
	$scope.editMode = false;
	$scope.lastThreeTransactions = [];
	$scope.allAdmins = [];
	$scope.homeObject = {
			balance: ""
	};
	
	loadRemoteData();

	function loadRemoteData(){
		balanceService.getBalance()
		.then(
				function(balance){
					$scope.homeObject.balance = balance;
					console.log("ba" + balance);
					console.log("ba2" + $scope.homeObject.balance);
				});
		serverTransactionsFactory.getLastThreeTransactions()
		.then(
				function(transactions){
					$scope.lastThreeTransactions = transactions;
				});
		userAccountFactory.getAdmins()
		.then(
				function(admins){
					$scope.allAdmins = admins; 
				});
	}

	$scope.openEmailModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalEmail.html',
			controller: ModalEmailInstanceController,
		});
		
		modalInstance.result.then(function(user){
			$scope.userMail = user;
			console.log($scope.userMail);
		});
	};
  
	  $scope.openPasswordModal = function () {
		  var modalPasswordInstance = $modal.open({
			  templateUrl: 'modalPassword.html',
			  controller: ModalPasswordInstanceController
		});
			
		modalPasswordInstance.result.then(function(user){
			$scope.userPassword = user;
			userAccountFactory.
			console.log($scope.userPassword);
		});
	  };
  
	  $scope.openAdminsModal = function () {
		  var modalAdminsInstance = $modal.open({
			  templateUrl: 'modalInviteAdmin.html',
			  controller: ModalAdminsInstanceController
		  });
		  
		  modalAdminsInstance.result.then(function(user){
			 $scope.userAdmins = user;
			 console.log($scope.userAdmins);
		  });
	  
	  };
};

var ModalEmailInstanceController = function ($scope, $modalInstance) {
	
	$scope.user = {
			old_email: "",
			new_email: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.user.old_email.length != 0 || $scope.user.new_email.length != 0){			
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

var ModalPasswordInstanceController = function ($scope, $modalInstance) {

	$scope.user = {
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

var ModalAdminsInstanceController = function ($scope, $modalInstance) {
	
	$scope.user = {
			email: "",
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