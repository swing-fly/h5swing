(function(define) {
	define([ 'text!shared/objectMapField.template.html' ], function f(htmlTemplate) {
		function wsObjectMapFieldDirective() {

			return {
				restrict : 'E',
				template : htmlTemplate,
				scope : {
					value : '=',
					variables : '=',
					readonly : '=',
					desc : '@',
					label : '@'
				},
				controllerAs : 'vm',
				bindToController : true,
				controller : wsObjectMapFieldDirectiveController
			};
		}

		function wsObjectMapFieldDirectiveController($scope, $element, $attrs) {
			var vm = this;
			vm.model = [];
			vm.addPair = addPair;
			vm.removePair = removePair;
			$scope.$watch("vm.value", function(value) {
				vm.model.splice(0, vm.model.length);
				angular.forEach(value, function(value, key) {
					vm.model.push({
						key : key,
						value : value
					});
				});
			}, true);

			$scope.$watch("vm.model", function(model) {
				var newValue = {};
				var valid = true;
				for (var i = 0; i < model.length; i++) {
					if (!newValue.hasOwnProperty(model[i].key)) {
						newValue[model[i].key] = model[i].value;
					} else {
						valid = false;
						model[i].error = 'Duplicate keys are not allowed! This field is ignored in output json.';
					}
				}
				if (valid) {
					vm.value = newValue;
				}
			}, true);


			function addPair(index) {
				vm.model.splice(index+1,0,{
					key : '',
					value : {}
				});
			}

			function removePair(index) {
				vm.model.splice(index, 1);
			}

		}

		wsObjectMapFieldDirectiveController.$inject = [ '$scope', '$element', '$attrs' ];

		return wsObjectMapFieldDirective;
	});
})(adminConsole.define);