import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import FormControlLabel from '@mui/material/FormControlLabel';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import Typography from '@mui/material/Typography';
import { toErrorMessage } from '@/api/axiosClient';
import { platformApi } from '@/api/platform';
import { FullPageLoader } from '@/components/LoadingState';
import { PageHeader } from '@/components/PageHeader';
import type { Permission, Role } from '@/types/domain';

/**
 * Roles are permission bundles, editable by an administrator. The sidebar and
 * every backend check read the same permission codes, so a change here changes
 * both what a role sees and what it may do.
 */
export function RolesPage() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [draft, setDraft] = useState<Set<string> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const roles = useQuery({ queryKey: ['roles'], queryFn: platformApi.roles });
  const permissions = useQuery({ queryKey: ['permissions'], queryFn: platformApi.permissions });

  const selected: Role | null = useMemo(() => {
    const list = roles.data ?? [];
    if (list.length === 0) {
      return null;
    }
    return list.find((role) => role.id === selectedId) ?? list[0];
  }, [roles.data, selectedId]);

  const current = draft ?? new Set(selected?.permissions ?? []);

  const grouped = useMemo(() => {
    const map = new Map<string, Permission[]>();
    (permissions.data ?? []).forEach((permission) => {
      const bucket = map.get(permission.module) ?? [];
      bucket.push(permission);
      map.set(permission.module, bucket);
    });
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [permissions.data]);

  const save = useMutation({
    mutationFn: () => platformApi.updateRolePermissions(selected!.id, [...current]),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['roles'] });
      setDraft(null);
      setSaved(true);
      setError(null);
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  if (roles.isLoading || permissions.isLoading) {
    return <FullPageLoader label="Loading roles" />;
  }

  const dirty = draft !== null;

  return (
    <Box>
      <PageHeader
        eyebrow="Administration"
        title="Roles & permissions"
        description="A permission says what a role may do. Its data scope says to whom. Both are enforced server-side."
        actions={
          <Button
            variant="contained"
            disabled={!dirty || current.size === 0 || save.isPending}
            onClick={() => save.mutate()}
          >
            {save.isPending ? 'Saving…' : 'Save permissions'}
          </Button>
        }
      />

      {error && (
        <Alert severity="error" sx={{ mb: 4 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {saved && !error && (
        <Alert severity="success" sx={{ mb: 4 }} onClose={() => setSaved(false)}>
          Permissions updated. Affected users pick up the change at their next sign-in or token refresh.
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', md: '280px 1fr' } }}>
        <Card sx={{ alignSelf: 'start' }}>
          <List disablePadding>
            {(roles.data ?? []).map((role) => {
              const active = selected?.id === role.id;
              return (
                <ListItemButton
                  key={role.id}
                  selected={active}
                  onClick={() => {
                    setSelectedId(role.id);
                    setDraft(null);
                    setSaved(false);
                  }}
                  sx={{ px: 4, py: 3, borderRadius: 0, display: 'block' }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                    <Typography sx={{ fontSize: 13.5, fontWeight: active ? 600 : 500 }}>
                      {role.name}
                    </Typography>
                    <Chip label={role.permissionCount} size="small" />
                  </Box>
                  <Typography variant="caption" sx={{ color: 'text.secondary', textTransform: 'capitalize' }}>
                    {role.dataScope.toLowerCase()} scope
                  </Typography>
                </ListItemButton>
              );
            })}
          </List>
        </Card>

        <Card>
          <CardContent sx={{ p: 5 }}>
            {selected ? (
              <>
                <Typography variant="h5">{selected.name}</Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1 }}>
                  {selected.description}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {current.size} of {(permissions.data ?? []).length} permissions granted
                  {selected.systemRole ? ' · system role' : ''}
                </Typography>

                <Divider sx={{ my: 4 }} />

                {grouped.map(([module, items]) => (
                  <Box key={module} sx={{ mb: 5 }}>
                    <Typography variant="overline" sx={{ color: 'primary.main', display: 'block', mb: 1 }}>
                      {module.toLowerCase()}
                    </Typography>
                    <Box
                      sx={{
                        display: 'grid',
                        gap: 1,
                        gridTemplateColumns: { xs: '1fr', lg: '1fr 1fr' },
                      }}
                    >
                      {items.map((permission) => (
                        <FormControlLabel
                          key={permission.code}
                          sx={{ alignItems: 'flex-start', m: 0, py: 1 }}
                          control={
                            <Checkbox
                              size="small"
                              checked={current.has(permission.code)}
                              onChange={(event) => {
                                const next = new Set(current);
                                if (event.target.checked) {
                                  next.add(permission.code);
                                } else {
                                  next.delete(permission.code);
                                }
                                setDraft(next);
                                setSaved(false);
                              }}
                            />
                          }
                          label={
                            <Box sx={{ pt: 0.5 }}>
                              <Typography variant="subtitle2">{permission.name}</Typography>
                              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                                {permission.description ?? permission.code}
                              </Typography>
                            </Box>
                          }
                        />
                      ))}
                    </Box>
                  </Box>
                ))}
              </>
            ) : (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                No roles defined.
              </Typography>
            )}
          </CardContent>
        </Card>
      </Box>
    </Box>
  );
}
