'use strict';

/**
 * LogoutController
 * @constructor
 */

function MessagesController($rootScope, $scope, $route, $location, messagesFactory) {
	
	loadRemoteData();

	$scope.updatedAccount = {
			url: "",
			trustLevel: "",
			creationDate: "",
			messageId: ""
	};

	function loadRemoteData(){
		$rootScope.initialized = true;
		messagesFactory.getMessages().then(function(data){
			$scope.messages = data.messagesList; 
		});
	}
	
	$scope.accept = function(url, trustLevel, creationDate, messageId){
		$scope.updatedAccount.url = url;
		$scope.updatedAccount.trustLevel = trustLevel;
		$scope.updatedAccount.creationDate = creationDate;
		$scope.updatedAccount.messageId = messageId;
		var account = $scope.updatedAccount;
		messagesFactory.accept(account).then(function(data){
			loadRemoteData();
		});
	};

	$scope.decline = function(url, trustLevel, creationDate, messageId){
		$scope.updatedAccount.url = url;
		$scope.updatedAccount.trustLevel = trustLevel;
		$scope.updatedAccount.creationDate = creationDate;
		$scope.updatedAccount.messageId = messageId;
		var account = $scope.updatedAccount;
		messagesFactory.decline(account).then(function(data){
			loadRemoteData();
		});
	};
};