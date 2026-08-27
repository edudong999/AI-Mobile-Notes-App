"""Note↔Category M:N tests."""
import pytest


async def _make_note(client, headers) -> int:
    r = await client.post("/api/v1/notes", headers=headers, json={"title": "t"})
    assert r.status_code == 200, r.text
    return r.json()["data"]["noteId"]


@pytest.mark.asyncio
async def test_set_note_categories_round_trip(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}

    cat_ids = []
    for n in ["数学", "错题"]:
        r = await client.post("/api/v1/categories", headers=headers, json={"name": n})
        cat_ids.append(r.json()["data"]["categoryId"])

    note_id = await _make_note(client, headers)
    r = await client.post(f"/api/v1/notes/{note_id}/categories", headers=headers,
                          json={"categoryIds": cat_ids})
    assert r.status_code == 200, r.text
    assert sorted(r.json()["data"]["categoryIds"]) == sorted(cat_ids)

    r = await client.get(f"/api/v1/notes/{note_id}", headers=headers)
    assert sorted(r.json()["data"]["categories"]) == sorted(cat_ids)


@pytest.mark.asyncio
async def test_set_note_categories_empty_clears(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    cat_id = (await client.post("/api/v1/categories", headers=headers,
                                json={"name": "x"})).json()["data"]["categoryId"]
    note_id = await _make_note(client, headers)

    await client.post(f"/api/v1/notes/{note_id}/categories", headers=headers,
                      json={"categoryIds": [cat_id]})
    r = await client.post(f"/api/v1/notes/{note_id}/categories", headers=headers,
                          json={"categoryIds": []})
    assert r.status_code == 200
    assert r.json()["data"]["categoryIds"] == []

    r = await client.get(f"/api/v1/notes/{note_id}", headers=headers)
    assert r.json()["data"]["categories"] == []


@pytest.mark.asyncio
async def test_set_note_categories_unknown_id_404(client, registered_user):
    headers = {"Authorization": f"Bearer {registered_user['token']}"}
    note_id = await _make_note(client, headers)
    r = await client.post(f"/api/v1/notes/{note_id}/categories", headers=headers,
                          json={"categoryIds": [99999]})
    assert r.status_code == 404