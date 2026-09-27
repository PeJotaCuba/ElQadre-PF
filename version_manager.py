#!/usr/bin/env python3
"""
ElQadre Version Manager
========================
Mecanismo único y automático de versionado para ElQadre.

Reglas:
1. Formato: MAYOR.MENOR.PARCHE (ej. 1.1.0)
2. MENOR: Identifica una nueva etapa de trabajo asociada a un rol.
3. PARCHE: Identifica cambios sucesivos realizados dentro de esa misma etapa/rol.
4. Mismo rol -> PARCHE + 1, VERSION_CODE + 1
5. Nuevo rol -> MENOR + 1, PARCHE = 0, VERSION_CODE + 1
6. Sincroniza simultáneamente version.properties y version.json
"""

import sys
import os
import json
import argparse
from datetime import datetime, timezone

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROPERTIES_PATH = os.path.join(BASE_DIR, "version.properties")
VERSION_JSON_PATH = os.path.join(BASE_DIR, "version.json")


def load_properties():
    props = {
        "MAJOR": "1",
        "MINOR": "1",
        "PATCH": "0",
        "VERSION_CODE": "2",
        "VERSION_NAME": "1.1.0",
        "CURRENT_STAGE_ROLE": "DUEÑO",
        "PRODUCT_NAME": "ElQadrePF",
        "APK_URL": "https://github.com/PeJotaCuba/BD-Qadre-PF/releases/download/v1.1/ElQadrePF.apk",
        "RELEASE_DATE": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "NOTES": "Actualización ElQadre"
    }

    if os.path.exists(PROPERTIES_PATH):
        with open(PROPERTIES_PATH, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    props[k.strip()] = v.strip()

    return props


def save_properties(props):
    content = f"""# =====================================================================
# ElQadre Project Version Control - SINGLE SOURCE OF TRUTH
# =====================================================================
# Regla de versionado: MAYOR.MENOR.PARCHE (Ej: 1.1.0)
# - MENOR: Identifica una nueva etapa de trabajo asociada a un rol.
# - PARCHE: Identifica modificaciones sucesivas dentro de la misma etapa.
# =====================================================================
MAJOR={props['MAJOR']}
MINOR={props['MINOR']}
PATCH={props['PATCH']}
VERSION_CODE={props['VERSION_CODE']}
VERSION_NAME={props['VERSION_NAME']}
CURRENT_STAGE_ROLE={props['CURRENT_STAGE_ROLE']}
PRODUCT_NAME={props['PRODUCT_NAME']}
APK_URL={props['APK_URL']}
RELEASE_DATE={props['RELEASE_DATE']}
NOTES={props['NOTES']}
"""
    with open(PROPERTIES_PATH, "w", encoding="utf-8") as f:
        f.write(content)


def save_version_json(props):
    data = {
        "product": props.get("PRODUCT_NAME", "ElQadrePF"),
        "versionCode": int(props.get("VERSION_CODE", 1)),
        "versionName": props.get("VERSION_NAME", "1.0.0"),
        "apkUrl": props.get("APK_URL", ""),
        "releaseDate": props.get("RELEASE_DATE", datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")),
        "notes": props.get("NOTES", "")
    }
    with open(VERSION_JSON_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def normalize_role(role_name):
    if not role_name:
        return "DUEÑO"
    r = role_name.strip().upper()
    if r in ["DUENO", "DUEÑO", "OWNER"]:
        return "DUEÑO"
    if r in ["CAJERO", "CASHIER"]:
        return "CAJERO"
    if r in ["BARRA", "BARTENDER"]:
        return "BARRA"
    if r in ["COCINA", "CHEF", "COCINERO"]:
        return "COCINA"
    if r in ["SALON", "MESERO", "DEPENDIENTE"]:
        return "SALON"
    if r in ["ADMIN", "ADMINISTRADOR"]:
        return "ADMIN"
    return r


def calculate_next_version(current_props, target_role, notes=""):
    major = int(current_props.get("MAJOR", 1))
    minor = int(current_props.get("MINOR", 0))
    patch = int(current_props.get("PATCH", 0))
    vcode = int(current_props.get("VERSION_CODE", 1))
    current_role = normalize_role(current_props.get("CURRENT_STAGE_ROLE", ""))
    target_role_norm = normalize_role(target_role)

    if current_role == target_role_norm and minor > 0:
        # Mismo rol: incrementar únicamente PATCH
        new_minor = minor
        new_patch = patch + 1
    else:
        # Nuevo rol o primer rol: incrementar MINOR y reiniciar PATCH a 0
        new_minor = minor + 1
        new_patch = 0

    new_vcode = vcode + 1
    new_version_name = f"{major}.{new_minor}.{new_patch}"
    iso_date = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    new_props = dict(current_props)
    new_props["MAJOR"] = str(major)
    new_props["MINOR"] = str(new_minor)
    new_props["PATCH"] = str(new_patch)
    new_props["VERSION_CODE"] = str(new_vcode)
    new_props["VERSION_NAME"] = new_version_name
    new_props["CURRENT_STAGE_ROLE"] = target_role_norm
    new_props["RELEASE_DATE"] = iso_date
    if notes:
        new_props["NOTES"] = notes

    return new_props


def main():
    parser = argparse.ArgumentParser(description="ElQadre Automated Version Manager")
    parser.add_argument("--role", help="Nombre del rol/etapa a declarar (ej. DUEÑO, CAJERO, BARRA, COCINA, SALON, ADMIN)")
    parser.add_argument("--notes", default="", help="Notas de la versión")
    parser.add_argument("--status", action="store_true", help="Muestra la versión actual sin modificar")
    parser.add_argument("--sync", action="store_true", help="Sincroniza version.json con version.properties")

    args = parser.parse_args()

    props = load_properties()

    if args.status:
        print(f"Versión Actual: {props['VERSION_NAME']}")
        print(f"VersionCode:    {props['VERSION_CODE']}")
        print(f"Etapa/Rol:      {props['CURRENT_STAGE_ROLE']}")
        print(f"Fecha:          {props['RELEASE_DATE']}")
        print(f"Notas:          {props['NOTES']}")
        return

    if args.sync:
        save_version_json(props)
        print(f"version.json sincronizado con versión {props['VERSION_NAME']} (code {props['VERSION_CODE']})")
        return

    if not args.role:
        print("Uso: python3 version_manager.py --role <ROL> [--notes <NOTAS>]")
        print(f"Versión actual: {props['VERSION_NAME']} (Rol: {props['CURRENT_STAGE_ROLE']})")
        return

    target_role = args.role
    old_version = props.get("VERSION_NAME", "1.0.0")
    old_role = props.get("CURRENT_STAGE_ROLE", "DUEÑO")
    
    new_props = calculate_next_version(props, target_role, args.notes)
    save_properties(new_props)
    save_version_json(new_props)

    print(f"==================================================")
    print(f"✓ Versión actualizada con éxito:")
    print(f"  Anterior: {old_version} (Rol: {old_role})")
    print(f"  Nueva:    {new_props['VERSION_NAME']} (Rol: {new_props['CURRENT_STAGE_ROLE']})")
    print(f"  VersionCode: {new_props['VERSION_CODE']}")
    print(f"  version.properties y version.json actualizados.")
    print(f"==================================================")


if __name__ == "__main__":
    main()
