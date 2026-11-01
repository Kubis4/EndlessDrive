shader_type spatial;
render_mode diffuse_lambert, specular_schlick_ggx;
// The ground on a phone where tyres have made fine patches: see ground_phone.gdshaderinc.
#define FINE_PATCHES
#include "res://offroad/ground_phone.gdshaderinc"
