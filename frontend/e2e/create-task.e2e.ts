import { expect, test } from '@playwright/test'
import { loadExampleEnv } from './stack.ts'

// CR-04 (docs/test-plan.md): a Manager signs in, opens a project, creates a
// task and sees it in the project's task list without reloading the page.
test('manager signs in, opens a project, creates a task and sees it in the list', async ({ page }) => {
  loadExampleEnv()
  const password = process.env.MATRIZ_DEMO_PASSWORD ?? ''
  // Unique per run, so the assertions never match a task left by another run.
  const title = `E2E task ${Date.now()}`

  await test.step('sign in as Ana (demo Manager of Website Redesign)', async () => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
    await page.getByLabel('Email').fill('ana@matriz.test')
    await page.getByLabel('Password').fill(password)
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeVisible()
  })

  await test.step('open the project', async () => {
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Projects' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Projects' })).toBeVisible()
    await page.getByRole('link', { name: 'Website Redesign' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Website Redesign' })).toBeVisible()
    await expect(page.getByText('Your role: Manager')).toBeVisible()
  })

  const tasks = page.getByRole('region', { name: 'Tasks', exact: true })
  await expect(tasks).toBeVisible()
  await expect(tasks.getByRole('link', { name: title })).toHaveCount(0)

  await test.step('create the task', async () => {
    await page.getByRole('button', { name: 'New task' }).click()
    const form = page.getByRole('region', { name: 'New task' })
    await form.getByLabel('Title', { exact: true }).fill(title)
    await form.getByLabel('Description', { exact: true }).fill('Created by the end-to-end test.')
    await form.getByRole('button', { name: 'Create task' }).click()
    await expect(page.getByText(`Task “${title}” created.`)).toBeVisible()
    await expect(form).toBeHidden()
  })

  await test.step('see the task in the list', async () => {
    const card = tasks
      .getByRole('list', { name: 'To do tasks' })
      .getByRole('listitem')
      .filter({ has: page.getByRole('link', { name: title, exact: true }) })
    await expect(card).toHaveCount(1)
    await expect(card.getByLabel(`Status of ${title}`)).toHaveValue('TODO')
    await expect(card.getByText('Unassigned', { exact: true })).toBeVisible()
    await expect(card.getByText('Medium', { exact: true })).toBeVisible()
  })

  await test.step('the task is stored (still listed after a reload)', async () => {
    await page.reload()
    await expect(tasks.getByRole('link', { name: title, exact: true })).toBeVisible()
  })
})
