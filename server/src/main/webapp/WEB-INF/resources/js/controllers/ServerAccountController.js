'use strict';

/**
 * ServerAccountController
 * @constructor
 */

function ServerAccountController($rootScope, $scope, $location, $routeParams, serverAccountFactory) {
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverAccountFactory.getServerAcccount($routeParams.serverId).then(function(account){
			$scope.serverAccount = account;
		});
	}
};