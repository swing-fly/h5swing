(function(define) {
	define([ 'views/config/configServer.controller',
	         'views/config/configSwing.controller', ], function f(serverCtrl, swingCtrl) {
		var module = angular.module('wsConfig', []);
		
		module.controller('ConfigServerController', serverCtrl);
		module.controller('ConfigSwingController', swingCtrl);

		module.run([ 'navigationService', function(navigationService) {
			var primary = navigationService.addLocation('Configuration', '#/config/server');
		} ]);

		module.config([ '$routeProvider', function($routeProvider) {
			$routeProvider.when('/config/server', {
				controller : 'ConfigServerController',
				controllerAs : 'vm',
				templateUrl : 'app/views/config/configServer.template.html'
			});
			$routeProvider.when('/config/swing/:path', {
				controller : 'ConfigSwingController',
				controllerAs : 'vm',
				templateUrl : 'app/views/config/configSwing.template.html'
			});
		} ]);
	});
})(adminConsole.define);