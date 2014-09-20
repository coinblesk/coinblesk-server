'use strict';

/**
 * RelationController
 * @constructor
 */
var RelationController = function( $rootScope, $scope, $modal, $location, serverAccountFactory) {
	$scope.trust = {};
	$scope.editMode = false;
	$scope.serveraccounts = {
			'url':'',
			'email': ''
	};
	$scope.trust = {
		'0':'No-Trust',
		'1':'Hyprid-Trust',
		'2':'Full-Trust'
	};
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverAccountFactory.getAccounts()
		.then(function(accounts){
					$scope.serveraccounts = accounts.serverAccountList; 
		});
	}
	
	$scope.openCreateAccountModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalnewAccount.html',
			controller: ModalAccountInstanceController
		});
		
		modalInstance.result.then(function(account){
			 $scope.serveraccounts.url = account.url;
			 $scope.serveraccounts.email = account.email;
			 if(!_.include($scope.serveraccounts, account.url)){
				 serverAccountFactory.createNewAccount($scope.serveraccounts).then(function(){
					 
				 });
			 }else{
				 $scope.setError("The url address " + url + " exists already!");
			 }
		});
	};
};

var ModalAccountInstanceController = function ($scope, $modalInstance) {
	
	$scope.account = {
			id: "",
			url: "",
			email: ""
	};
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.account.url.length != 0 && $scope.account.email.length != 0){
			console.log($scope.account.url + " " + $scope.account.email);
			$modalInstance.close($scope.account);
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