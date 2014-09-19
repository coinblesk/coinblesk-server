'use strict';

/**
 * LogoutController
 * @constructor
 */

function MessagesController($scope, $route, $location, messagesFactory) {
	
loadRemoteData();

	$scope.updatedAccount = {
			url: "",
			trustLevel: "",
			creationDate: ""
	};

	function loadRemoteData(){
		messagesFactory.getMessages().then(function(data){
			$scope.messages = data.messagesList; 
		});
	}
	
	$scope.accept = function(url, trustLevel,creationDate){
		$scope.updatedAccount.url = url;
		$scope.updatedAccount.trustLevel = trustLevel;
		$scope.updatedAccount.creationDate = creationDate;
		var account = $scope.updatedAccount;
		messagesFactory.accept(account).then(function(data){
			$route.reload();
		});
	};

	$scope.decline = function(url, trustLevel,creationDate){
		$scope.updatedAccount.url = url;
		$scope.updatedAccount.trustLevel = trustLevel;
		$scope.updatedAccount.creationDate = creationDate;
		account = $scope.updatedAccount;
		messagesFactory.decline(account).then(function(data){
			$route.reload();
		});
	};
};