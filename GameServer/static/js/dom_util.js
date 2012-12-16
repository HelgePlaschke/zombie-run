// This library should not depend on Prototype.

function switchView(id) {
  var foundElement = false;
  elements = document.body.childNodes;
  for (i = 0; i < elements.length; ++i) {
    element = elements[i];
    if (element.id == id) {
      element.className = "display";
      foundElement = true;
    } else if (element.className == "display") {
      element.className = "hidden";
    }
  }
  if (foundElement) {
    return true;
  } else {
    return false;
  }
}