"""Category router tests."""
import pytest


@pytest.mark.asyncio
async def test_create_list_delete(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}

    r = await client.post("/api/v1/categories", headers=headers, json={"name": "数学"})
    assert r.status_code == 200, r.text
    cid = r.json()["data"]["categoryId"]

    r = await client.get("/api/v1/categories", headers=headers)
    assert r.status_code == 200
    items = r.json()["data"]["list"]
    assert any(it["categoryId"] == cid and it["name"] == "数学" for it in items)
    assert all("folderId" not in it for it in items)

    r = await client.delete(f"/api/v1/categories/{cid}", headers=headers)
    assert r.status_code == 200

    r = await client.get("/api/v1/categories", headers=headers)
    assert all(it["categoryId"] != cid for it in r.json()["data"]["list"])


@pytest.mark.asyncio
async def test_create_duplicate_name_400(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    r1 = await client.post("/api/v1/categories", headers=headers, json={"name": "错题"})
    assert r1.status_code == 200
    r2 = await client.post("/api/v1/categories", headers=headers, json={"name": "错题"})
    assert r2.status_code == 400


@pytest.mark.asyncio
async def test_rename_via_patch(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    r = await client.post("/api/v1/categories", headers=headers, json={"name": "draft"})
    cid = r.json()["data"]["categoryId"]
    r = await client.patch(f"/api/v1/categories/{cid}", headers=headers, json={"name": "高数"})
    assert r.status_code == 200
    items = (await client.get("/api/v1/categories", headers=headers)).json()["data"]["list"]
    assert any(it["name"] == "高数" and it["categoryId"] == cid for it in items)


@pytest.mark.asyncio
async def test_reorder_changes_sort_index(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    ids = []
    for n in ["a", "b", "c"]:
        r = await client.post("/api/v1/categories", headers=headers, json={"name": n})
        ids.append(r.json()["data"]["categoryId"])
    new_order = [ids[2], ids[0], ids[1]]
    r = await client.post("/api/v1/categories/reorder", headers=headers,
                          json={"orderedIds": new_order})
    assert r.status_code == 200