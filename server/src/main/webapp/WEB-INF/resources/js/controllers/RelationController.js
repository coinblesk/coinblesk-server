'use strict';

/**
 * RelationController
 * @constructor
 */
var RelationController = function( $rootScope, $scope, $modal, $location, serverAccountFactory) {
	$scope.trust = {};
	$scope.editMode = false;
	$scope.serveraccounts = [];
	$scope.trust = {
		'0':'No-Trust',
		'1':'Hyprid-Trust',
		'2':'Full-Trust'
	};
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverAccountFactory.getAccounts()
		.then(function(accounts){
					$scope.serveraccounts = accounts; 
		});
	}
	
	$scope.openCreateAccountModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalnewAccount.html',
			controller: ModalAccountInstanceController
		});
		
		modalInstance.result.then(function(url){
			 $scope.serverRelation = url;
			 if(!_.include($scope.serveraccounts, url)){
				 serverAccountFactory.createNewAccount(url).then(function(){
					 
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
		if($scope.account.url.length != 0){			
			$modalInstance.close($scope.acccount);
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