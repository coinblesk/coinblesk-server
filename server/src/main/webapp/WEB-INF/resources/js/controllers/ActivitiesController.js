'use strict';

/**
 * ActivitiesController
 * @constructor
 */
var ActivitiesController = function($rootScope, $scope, $location, activitiesFactory) {
	loadRemoteData();
	
	function loadRemoteData(){
		$rootScope.initialized = true;
		if($rootScope.loggedusername!=undefined || $rootScope.loggedusername.length > 4){			
			$scope.loggeduser = $rootScope.loggedusername;
		}
		activitiesFactory.getActivities().then(function(logs){
					$scope.activitylogs = logs.activitiesList; 
		});
	}
};