(function(root, factory) {
	if (typeof define === "function" && define.amd) {
		// AMD
		define([ 'webswing-dd' ], factory);
	} else {
		root.WebswingBase = factory(root.WebswingDirectDraw);
	}
}(this, function WebswingBase(WebswingDirectDraw) {
	"use strict";

	var api;

	var timer1, timer2;
	var latestMouseMoveEvent = null;
	var latestMouseWheelEvent = null;
	var latestWindowResizeEvent = null;
	var mouseDown = 0;
	var inputEvtQueue = [];

	var windowImageHolders = {};
	var directDraw = new WebswingDirectDraw({});

	function startApplication(name, applet) {
		api.canvas.get();
		registerEventListeners(api.canvas.get(), api.canvas.getInput());
		resetState();
		api.context = {
			clientId : api.login.user() + api.identity.get() + name,
			appName : name,
			hasControl : true,
			mirrorMode : false,
			canPaint : true,
			applet : applet
		}
		handshake();
		api.dialog.show(api.dialog.content.startingDialog);
	}

	function startMirrorView(clientId, appName) {
		api.canvas.get();
		registerEventListeners(api.canvas.get(), api.canvas.getInput());
		resetState();
		api.context = {
			clientId : clientId,
			appName : appName,
			hasControl : false,
			mirrorMode : true,
			canPaint : true
		};
		handshake();
		repaint();
		api.dialog.show(api.dialog.content.startingDialog);
	}

	function continueSession() {
		api.dialog.hide();
		api.context.canPaint = true;
		handshake();
		repaint();
		ack();
	}

	function resetState() {
		api.context = {
			clientId : '',
			appName : null,
			hasControl : false,
			mirrorMode : false,
			canPaint : false
		};
		clearInterval(timer1);
		clearInterval(timer2);
		timer1 = setInterval(sendInput, 100);
		timer2 = setInterval(heartbeat, 10000);
		latestMouseMoveEvent = null;
		latestMouseWheelEvent = null;
		latestWindowResizeEvent = null;
		mouseDown = 0;
		inputEvtQueue = [];
		windowImageHolders = {};
		directDraw = new WebswingDirectDraw({});
	}

	function sendInput() {
		enqueueInputEvent();
		if (inputEvtQueue.length > 0) {
			api.socket.send({
				events : inputEvtQueue
			});
			inputEvtQueue = [];
		}

	}

	function enqueueMessageEvent(message) {
		inputEvtQueue.push(getMessageEvent(message));
	}

	function enqueueInputEvent(message) {
		if (api.context.hasControl) {
			if (latestMouseMoveEvent != null) {
				inputEvtQueue.push(latestMouseMoveEvent);
				latestMouseMoveEvent = null;
			}
			if (latestMouseWheelEvent != null) {
				inputEvtQueue.push(latestMouseWheelEvent);
				latestMouseWheelEvent = null;
			}
			if (latestWindowResizeEvent != null) {
				inputEvtQueue.push(latestWindowResizeEvent);
				latestWindowResizeEvent = null;
			}
			if (message != null) {
				if (JSON.stringify(inputEvtQueue[inputEvtQueue.length - 1]) !== JSON.stringify(message)) {
					inputEvtQueue.push(message);
				}
			}
		}
	}

	function heartbeat() {
		enqueueMessageEvent('hb');
	}

	function repaint() {
		enqueueMessageEvent('repaint');
	}

	function ack() {
		enqueueMessageEvent('paintAck');
	}

	function kill() {
		enqueueMessageEvent('killSwing');
	}

	function unload() {
		enqueueMessageEvent('unload');
	}

	function handshake() {
		inputEvtQueue.push(getHandShake(api.canvas.get()));
	}

	function dispose() {
		clearInterval(timer1);
		clearInterval(timer2);
		unload();
		sendInput();
		resetState();
		document.removeEventListener('mousedown', mouseDownEventHandler);
		document.removeEventListener('mouseout', mouseOutEventHandler);
		document.removeEventListener('mouseup', mouseUpEventHandler);
		window.removeEventListener('beforeunload', beforeUnloadEventHandler);
	}

	function processMessage(data) {
		if (data.applications != null && data.applications.length != 0) {
			api.selector.show(data.applications);
		}
		if (data.event != null) {
			if (data.event == "shutDownNotification") {
				api.dialog.show(api.dialog.content.stoppedDialog);
				api.socket.dispose();
				api.canvas.dispose();
				dispose();
			} else if (data.event == "applicationAlreadyRunning") {
				api.dialog.show(api.dialog.content.applicationAlreadyRunning);
			} else if (data.event == "tooManyClientsNotification") {
				api.dialog.show(api.dialog.content.tooManyClientsNotification);
			} else if (data.event == "continueOldSession") {
				api.context.canPaint = false;
				api.dialog.show(api.dialog.content.continueOldSessionDialog);
			}
			return;
		}
		if (data.jsRequest != null && api.context.mirrorMode == false) {
			api.jslink.process(data.jsRequest);
		}
		if (api.context.canPaint) {
			processRequest(api.canvas.get(), data);
		}
	}

	function processRequest(canvas, data) {
		api.dialog.hide();
		var context = canvas.getContext("2d");
		if (data.linkAction != null) {
			if (data.linkAction.action == 'url') {
				api.files.link(data.linkAction.src);
			} else if (data.linkAction.action == 'print') {
				api.files.print(encodeURIComponent(location.pathname + 'file?id=' + data.linkAction.src));
			} else if (data.linkAction.action == 'file') {
				api.files.download('file?id=' + data.linkAction.src);
			}
		}
		if (data.moveAction != null) {
			copy(data.moveAction.sx, data.moveAction.sy, data.moveAction.dx, data.moveAction.dy, data.moveAction.width, data.moveAction.height,
					context);
		}
		if (data.cursorChange != null && api.context.hasControl) {
			canvas.style.cursor = data.cursorChange.cursor;
		}
		if (data.copyEvent != null && api.context.hasControl) {
			api.clipboard.copy(data.copyEvent);
		}
		if (data.fileDialogEvent != null && api.context.hasControl) {
			if (data.fileDialogEvent.eventType === 'Open') {
				api.files.open(data.fileDialogEvent, api.context.clientId);
			} else if (data.fileDialogEvent.eventType === 'Close') {
				api.files.close();
			}
		}
		if (data.closedWindow != null) {
			delete windowImageHolders[data.closedWindow];
		}
		// firs is always the background
		for ( var i in data.windows) {
			var win = data.windows[i];
			if (win.id == 'BG') {
				if (api.context.mirrorMode) {
					adjustCanvasSize(canvas, win.width, win.height);
				}
				for ( var x in win.content) {
					var winContent = win.content[x];
					if (winContent != null) {
						clear(win.posX + winContent.positionX, win.posY + winContent.positionY, winContent.width, winContent.height, context);
					}
				}
				data.windows.splice(i, 1);
				break;
			}
		}
		// regular windows (background removed)
		if (data.windows != null) {
			data.windows.reduce(
					function(sequence, win) {
						if (win.directDraw != null) {
							// directdraw
							return sequence.then(function(resolved) {
								if (typeof win.directDraw === 'string') {
									return directDraw.draw64(win.directDraw, windowImageHolders[win.id]);
								} else {
									return directDraw.drawBin(win.directDraw, windowImageHolders[win.id]);
								}
							}).then(
									function(resultImage) {
										windowImageHolders[win.id] = resultImage;
										for ( var x in win.content) {
											var winContent = win.content[x];
											if (winContent != null) {
												context.drawImage(resultImage, winContent.positionX, winContent.positionY, winContent.width,
														winContent.height, win.posX + winContent.positionX, win.posY + winContent.positionY,
														winContent.width, winContent.height);
											}
										}
									});
						} else {
							// imagedraw
							return sequence.then(function(resolved) {
								return win.content.reduce(function(internalSeq, winContent) {
									return internalSeq.then(function(done) {
										return new Promise(function(resolved, rejected) {
											if (winContent != null) {
												var imageObj = new Image();
												var onloadFunction = function() {
													context.drawImage(imageObj, win.posX + winContent.positionX, win.posY + winContent.positionY);
													resolved();
													imageObj.onload = null;
													imageObj.src = '';
													if (imageObj.clearAttributes != null) {
														imageObj.clearAttributes();
													}
													imageObj = null;
												}
												imageObj.onload = function() {
													// fix for ie - onload is fired before the image is ready for rendering to canvas. This is
													// a ugly quickfix
													if (api.ieVersion && api.ieVersion <= 10) {
														window.setTimeout(onloadFunction, 20);
													} else {
														onloadFunction();
													}
												};
												imageObj.src = getImageString(winContent.base64Content);
											}
										});
									});
								}, Promise.resolve());
							});
						}
					}, Promise.resolve()).then(function() {
				ack();
			});
		}
	}

	function getImageString(data) {
		if (typeof data === 'object') {
			var binary = '';
			var bytes = new Uint8Array(data.buffer, data.offset, data.limit - data.offset);
			for ( var i = 0, l = bytes.byteLength; i < l; i++) {
				binary += String.fromCharCode(bytes[i]);
			}
			data = window.btoa(binary);
		}
		return 'data:image/png;base64,' + data
	}

	function adjustCanvasSize(canvas, width, height) {
		if (canvas.width != width || canvas.height != height) {
			var snapshot = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height);
			canvas.width = width;
			canvas.height = height;
			canvas.getContext("2d").putImageData(snapshot, 0, 0);
		}
	}

	function clear(x, y, w, h, context) {
		context.clearRect(x, y, w, h);
	}

	function copy(sx, sy, dx, dy, w, h, context) {
		var copy = context.getImageData(sx, sy, w, h);
		context.putImageData(copy, dx, dy);
	}

	function registerEventListeners(canvas, input) {
		bindEvent(canvas, 'mousedown', function(evt) {
			var mousePos = getMousePos(canvas, evt, 'mousedown');
			latestMouseMoveEvent = null;
			enqueueInputEvent(mousePos);
			focusInput(input);
			return false;
		}, false);
		bindEvent(canvas, 'dblclick', function(evt) {
			var mousePos = getMousePos(canvas, evt, 'dblclick');
			latestMouseMoveEvent = null;
			enqueueInputEvent(mousePos);
			focusInput(input);
			return false;
		}, false);
		bindEvent(canvas, 'mousemove', function(evt) {
			var mousePos = getMousePos(canvas, evt, 'mousemove');
			mousePos.mouse.button = mouseDown;
			latestMouseMoveEvent = mousePos;
			return false;
		}, false);
		bindEvent(canvas, 'mouseup', function(evt) {
			var mousePos = getMousePos(canvas, evt, 'mouseup');
			latestMouseMoveEvent = null;
			enqueueInputEvent(mousePos);
			focusInput(input);
			return false;
		}, false);
		// IE9, Chrome, Safari, Opera
		bindEvent(canvas, "mousewheel", function(evt) {
			var mousePos = getMousePos(canvas, evt, 'mousewheel');
			latestMouseMoveEvent = null;
			if (latestMouseWheelEvent != null) {
				mousePos.mouse.wheelDelta += latestMouseWheelEvent.mouse.wheelDelta;
			}
			latestMouseWheelEvent = mousePos;
			return false;
		}, false);
		// firefox
		bindEvent(canvas, "DOMMouseScroll", function(evt) {
			var mousePos = getMousePos(canvas, evt, 'mousewheel');
			latestMouseMoveEvent = null;
			if (latestMouseWheelEvent != null) {
				mousePos.mouse.wheelDelta += latestMouseWheelEvent.mouse.wheelDelta;
			}
			latestMouseWheelEvent = mousePos;
			return false;
		}, false);
		bindEvent(canvas, 'contextmenu', function(event) {
			event.preventDefault();
			event.stopPropagation();
			return false;
		});

		bindEvent(input, 'keydown', function(event) {
			// 48-57
			// 65-90
			// 186-192
			// 219-222
			// 226
			// FF (163, 171, 173, ) -> en layout ]\/ keys
			var kc = event.keyCode;
			if (!((kc >= 48 && kc <= 57) || (kc >= 65 && kc <= 90) || (kc >= 186 && kc <= 192) || (kc >= 219 && kc <= 222) || (kc == 226)
					|| (kc == 0) || (kc == 163) || (kc == 171) || (kc == 173) || (kc >= 96 && kc <= 111) || (kc == 59) || (kc == 61))) {
				event.preventDefault();
				event.stopPropagation();
			}
			var keyevt = getKBKey('keydown', canvas, event);
			// hanle paste event
			if (keyevt.key.ctrl && (keyevt.key.character == 86 || keyevt.key.character == 118)) { // ctrl+v
				// paste handled in paste event
			} else {
				// default action prevented
				if (keyevt.key.ctrl && !keyevt.key.alt && !keyevt.key.altgr) {
					event.preventDefault();
				}
				enqueueInputEvent(keyevt);
			}
			return false;
		}, false);
		bindEvent(input, 'keypress', function(event) {
			var keyevt = getKBKey('keypress', canvas, event);
			if (!(keyevt.key.ctrl && keyevt.key.character == 118)) { // skip ctrl+v
				event.preventDefault();
				event.stopPropagation();
				enqueueInputEvent(keyevt);
			}
			return false;
		}, false);
		bindEvent(input, 'keyup', function(event) {
			var keyevt = getKBKey('keyup', canvas, event);
			if (!(keyevt.key.ctrl && keyevt.key.character == 118)) { // skip ctrl+v
				event.preventDefault();
				event.stopPropagation();
			}
			enqueueInputEvent(keyevt);
			return false;
		}, false);
		bindEvent(input, 'paste', function(event) {
			event.preventDefault();
			event.stopPropagation();
			api.clipboard.paste(event.clipboardData);
			return false;
		}, false);

		bindEvent(document, 'mousedown', mouseDownEventHandler);
		bindEvent(document, 'mouseout', mouseOutEventHandler);
		bindEvent(document, 'mouseup', mouseUpEventHandler);
		bindEvent(window, 'beforeunload', beforeUnloadEventHandler);
	}

	function mouseDownEventHandler(evt) {
		if (evt.which == 1) {
			mouseDown = 1;
		}
	}
	function mouseOutEventHandler(evt) {
		mouseDown = 0;
	}
	function mouseUpEventHandler(evt) {
		if (evt.which == 1) {
			mouseDown = 0;
		}
	}
	function beforeUnloadEventHandler(evt) {
		dispose();
	}

	function focusInput(input) {
		// In order to ensure that the browser will fire clipboard events, we always need to have something selected
		input.value = ' ';
		input.focus();
		input.select();
	}

	function getMousePos(canvas, evt, type) {
		var rect = canvas.getBoundingClientRect();
		var root = document.documentElement;
		// return relative mouse position
		var mouseX = Math.round(evt.clientX - rect.left);
		var mouseY = Math.round(evt.clientY - rect.top);
		var delta = 0;
		if (type == 'mousewheel') {
			delta = -Math.max(-1, Math.min(1, (evt.wheelDelta || -evt.detail)));
		}
		return {
			mouse : {
				x : mouseX,
				y : mouseY,
				type : type,
				wheelDelta : delta,
				button : evt.which,
				ctrl : evt.ctrlKey,
				alt : evt.altKey,
				shift : evt.shiftKey,
				meta : evt.metaKey
			}
		};
	}

	function getKBKey(type, canvas, evt) {
		var char = evt.which;
		if (char == 0 && evt.key != null) {
			char = evt.key.charCodeAt(0);
		}
		var kk = evt.keyCode;
		if (kk == 0) {
			kk = char;
		}
		return {
			key : {
				type : type,
				character : char,
				keycode : kk,
				alt : evt.altKey,
				ctrl : evt.ctrlKey,
				shift : evt.shiftKey,
				meta : evt.metaKey
			}
		};
	}

	function getHandShake(canvas) {
		var handshake = {
			applicationName : api.context.appName,
			clientId : api.context.clientId,
			sessionId : api.socket.uuid(),
			mirrored : api.context.mirrorMode,
			directDrawSupported : api.typedArraysSupported
		}

		if (!api.context.mirrorMode) {
			handshake.applet = api.context.applet;
			handshake.documentBase = api.documentBase;
			handshake.params = api.params;
			handshake.desktopWidth = canvas.offsetWidth;
			handshake.desktopHeight = canvas.offsetHeight;
		}
		return {
			handshake : handshake
		};
	}

	function getMessageEvent(message) {
		return {
			event : {
				type : message,
			}
		};
	}

	function bindEvent(el, eventName, eventHandler) {
		if (el.addEventListener != null) {
			el.addEventListener(eventName, eventHandler);
		}
	}

	return {
		init : function(wsApi) {
			api = wsApi;
			api.context = {
				clientId : '',
				appName : null,
				hasControl : false,
				mirrorMode : false,
				canPaint : false
			};
			api.base = {
				startApplication : startApplication,
				startMirrorView : startMirrorView,
				continueSession : continueSession,

				kill : kill,
				handshake : handshake,
				processMessage : processMessage,
				dispose : dispose
			};
		}
	};
}));
