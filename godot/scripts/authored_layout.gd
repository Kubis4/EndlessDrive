class_name AuthoredLayout
extends RefCounted
## Stable per-node bindings survive renaming/reparenting in the scene editor.
## annotate() is used ONLY by the one-off authoring tool, never during play.

static func annotate(controller: Node) -> void:
	for property: Dictionary in controller.get_property_list():
		if int(property.usage) & PROPERTY_USAGE_SCRIPT_VARIABLE:
			_mark(controller.get(property.name), str(property.name), controller)


static func _mark(value: Variant, binding: String, root: Node) -> void:
	if value is Node and root.is_ancestor_of(value):
		var bindings: Array = value.get_meta("layout_bindings", [])
		if not bindings.has(binding): bindings.append(binding)
		value.set_meta("layout_bindings", bindings)
		if not str(value.name).begins_with("Authored"):
			value.name = binding.trim_prefix("_").replace("/", "_").to_pascal_case()
	elif value is Dictionary:
		for key: Variant in value:
			_mark(value[key], binding + "/" + str(key), root)


static func bind_nodes(controller: Node, subtree: Node) -> void:
	for binding: String in subtree.get_meta("layout_bindings", []):
		var keys := binding.split("/")
		if keys.size() == 1:
			controller.set(keys[0], subtree)
		else:
			var data: Dictionary = controller.get(keys[0])
			for index in range(1, keys.size() - 1):
				if not data.has(keys[index]): data[keys[index]] = {}
				data = data[keys[index]]
			data[keys[-1]] = subtree
	for child: Node in subtree.get_children(): bind_nodes(controller, child)
